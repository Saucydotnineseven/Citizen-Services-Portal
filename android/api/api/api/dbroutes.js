
// All API endpoints for the Home Affairs Assistant App.

const pool = require('./db');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

// Only allows logged-in users through
function requireLogin(req, res, next) {
  const token = (req.headers.authorization || '').replace('Bearer ', '');

  try {
    req.user = jwt.verify(token, process.env.JWT_SECRET);
    next();
  } catch (e) {
    res.status(401).json({ error: 'Please log in' });
  }
}

// Only allows OFFICIAL / ADMIN accounts through
function requireStaff(req, res, next) {
  if (req.user.role !== 'OFFICIAL' && req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Officials only' });
  }

  next();
}

module.exports = function (app) {

  // ---------- Database health ----------
  app.get('/api/health/db', async (req, res) => {
    try {
      await pool.query('SELECT 1');
      res.json({ database: 'connected' });
    } catch (e) {
      res.status(500).json({ database: 'error', message: e.message });
    }
  });

  // ---------- Service catalogue ----------
  app.get('/api/services', async (req, res) => {
    try {
      const [rows] = await pool.query(
        `SELECT s.*, c.category_name
         FROM services s
         JOIN service_categories c ON s.category_id = c.category_id
         ORDER BY c.category_id, s.service_name`
      );

      res.json(rows);
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ---------- Register ----------
  app.post('/api/register', async (req, res) => {
    const {
      idNumber,
      firstName,
      lastName,
      email,
      phone,
      password,
      role
    } = req.body;

    if (!idNumber || !email || !password ||
        password.length < 6 || !firstName) {
      return res.status(400).json({
        error: 'Fill in all fields (password 6+ characters)'
      });
    }

    const safeRole =
      role === 'FOREIGN_NATIONAL' ? 'FOREIGN_NATIONAL' : 'CITIZEN';

    try {
      const hash = await bcrypt.hash(password, 10);

      const [result] = await pool.query(
        `INSERT INTO users
         (id_number, first_name, last_name, email, phone, password_hash, role)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
        [
          idNumber,
          firstName,
          lastName || '',
          email,
          phone || null,
          hash,
          safeRole
        ]
      );

      res.json({
        userId: result.insertId,
        role: safeRole
      });

    } catch (e) {
      if (e.code === 'ER_DUP_ENTRY') {
        return res.status(409).json({
          error: 'ID number or email already registered'
        });
      }

      res.status(500).json({ error: e.message });
    }
  });

  // ---------- Login ----------
  app.post('/api/login', async (req, res) => {
    try {
      const { idNumber, email, password } = req.body;
      const who = idNumber || email || '';

      const [rows] = await pool.query(
        'SELECT * FROM users WHERE id_number = ? OR email = ?',
        [who, who]
      );

      const user = rows[0];

      if (!user || !(await bcrypt.compare(password || '', user.password_hash))) {
        return res.status(401).json({
          error: 'Wrong ID number or password'
        });
      }

      const token = jwt.sign(
        { userId: user.user_id, role: user.role },
        process.env.JWT_SECRET,
        { expiresIn: '7d' }
      );

      res.json({
        token,
        userId: user.user_id,
        role: user.role,
        firstName: user.first_name
      });

    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ---------- Who am I ----------
  app.get('/api/me', requireLogin, async (req, res) => {
    try {
      const [rows] = await pool.query(
        `SELECT user_id, first_name, last_name, email, role, face_verified
         FROM users
         WHERE user_id = ?`,
        [req.user.userId]
      );

      res.json(rows[0] || null);

    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ---------- Submit an application ----------
  app.post('/api/applications', requireLogin, async (req, res) => {
    const { serviceId, formData } = req.body;
    let conn;

    try {
      conn = await pool.getConnection();
      await conn.beginTransaction();

      const [result] = await conn.query(
        `INSERT INTO applications
         (user_id, service_id, current_stage, status, form_data)
         VALUES (?, ?, 'Documents Verified', 'SUBMITTED', ?)`,
        [
          req.user.userId,
          serviceId,
          JSON.stringify(formData || {})
        ]
      );

      const appId = result.insertId;

      const stages = [
        'Submitted',
        'Documents Verified',
        'Under Review',
        'Approved',
        'Ready for Collection'
      ];

      for (let i = 0; i < stages.length; i++) {
        const status =
          i === 0 ? 'DONE' :
          i === 1 ? 'CURRENT' : 'PENDING';

        await conn.query(
          `INSERT INTO application_stages
           (application_id, stage_order, stage_name, stage_status, updated_date)
           VALUES (?, ?, ?, ?, ?)`,
          [
            appId,
            i + 1,
            stages[i],
            status,
            i <= 1 ? new Date() : null
          ]
        );
      }

      await conn.commit();
      res.json({ applicationId: appId });

    } catch (e) {
      if (conn) await conn.rollback();
      res.status(500).json({ error: e.message });

    } finally {
      if (conn) conn.release();
    }
  });

  // ---------- My applications ----------
  app.get('/api/applications/mine', requireLogin, async (req, res) => {
    try {
      const [apps] = await pool.query(
        `SELECT a.*, s.service_name
         FROM applications a
         JOIN services s ON a.service_id = s.service_id
         WHERE a.user_id = ?
         ORDER BY a.submission_date DESC`,
        [req.user.userId]
      );

      for (const a of apps) {
        const [stages] = await pool.query(
          `SELECT stage_name, stage_status, updated_date
           FROM application_stages
           WHERE application_id = ?
           ORDER BY stage_order`,
          [a.application_id]
        );

        a.stages = stages;
      }

      res.json(apps);

    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ---------- Branches ----------
  app.get('/api/branches', requireLogin, async (req, res) => {
    try {
      const [rows] = await pool.query(
        'SELECT * FROM branches ORDER BY branch_name'
      );

      res.json(rows);

    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ---------- Appointments ----------
  app.post('/api/appointments', requireLogin, async (req, res) => {
    const { serviceId, branchId, dateTime } = req.body;

    try {
      const [r] = await pool.query(
        `INSERT INTO appointments
         (user_id, service_id, branch_id, appointment_datetime)
         VALUES (?, ?, ?, ?)`,
        [req.user.userId, serviceId, branchId, dateTime]
      );

      res.json({ appointmentId: r.insertId });

    } catch (e) {
      if (e.code === 'ER_DUP_ENTRY') {
        return res.status(409).json({
          error: 'That time slot is already booked'
        });
      }

      res.status(500).json({ error: e.message });
    }
  });

  app.get('/api/appointments/mine', requireLogin, async (req, res) => {
    try {
      const [rows] = await pool.query(
        `SELECT ap.appointment_id, ap.appointment_datetime, ap.status,
                s.service_name, b.branch_name, b.city
         FROM appointments ap
         JOIN services s ON ap.service_id = s.service_id
         JOIN branches b ON ap.branch_id = b.branch_id
         WHERE ap.user_id = ?
         ORDER BY ap.appointment_datetime`,
        [req.user.userId]
      );

      res.json(rows);

    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ---------- Simulated payment ----------
  app.post('/api/payments', requireLogin, async (req, res) => {
    try {
      const { applicationId, method } = req.body;

      const [rows] = await pool.query(
        `SELECT s.fee
         FROM applications a
         JOIN services s ON a.service_id = s.service_id
         WHERE a.application_id = ? AND a.user_id = ?`,
        [applicationId, req.user.userId]
      );

      if (rows.length === 0) {
        return res.status(404).json({
          error: 'Application not found'
        });
      }

      const fee = Number(rows[0].fee);

      if (fee <= 0) {
        return res.json({
          paid: false,
          message: 'No fee for this service'
        });
      }

      const [r] = await pool.query(
        `INSERT INTO payments
         (application_id, amount, payment_method, payment_status)
         VALUES (?, ?, ?, 'PAID')`,
        [applicationId, fee, method || 'CARD']
      );

      res.json({
        paid: true,
        paymentId: r.insertId,
        amount: fee
      });

    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ---------- Face verification demo ----------
  app.post('/api/face-verify', requireLogin, async (req, res) => {
    try {
      await pool.query(
        'UPDATE users SET face_verified = 1 WHERE user_id = ?',
        [req.user.userId]
      );

      res.json({ faceVerified: true });

    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ---------- Notifications ----------
  app.get('/api/notifications/mine', requireLogin, async (req, res) => {
    try {
      const [rows] = await pool.query(
        `SELECT notification_id, message, date_sent
         FROM notifications
         WHERE user_id = ?
         ORDER BY date_sent DESC`,
        [req.user.userId]
      );

      res.json(rows);

    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ---------- Official: all applications ----------
  app.get('/api/staff/applications', requireLogin, requireStaff, async (req, res) => {
    try {
      const [rows] = await pool.query(
        `SELECT a.application_id, a.status, a.current_stage,
                a.submission_date, s.service_name,
                u.first_name, u.last_name
         FROM applications a
         JOIN services s ON a.service_id = s.service_id
         JOIN users u ON a.user_id = u.user_id
         ORDER BY a.submission_date DESC`
      );

      res.json(rows);

    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ---------- Official: advance application ----------
  app.post('/api/staff/applications/:id/advance', requireLogin, requireStaff, async (req, res) => {
    const id = Number(req.params.id);
    let conn;

    try {
      conn = await pool.getConnection();
      await conn.beginTransaction();

      const [stages] = await conn.query(
        `SELECT * FROM application_stages
         WHERE application_id = ?
         ORDER BY stage_order FOR UPDATE`,
        [id]
      );

      const idx = stages.findIndex(
        s => s.stage_status === 'CURRENT'
      );

      if (idx === -1) {
        await conn.rollback();
        return res.status(400).json({
          error: 'Nothing left to advance'
        });
      }

      await conn.query(
        `UPDATE application_stages
         SET stage_status = 'DONE', updated_date = NOW()
         WHERE stage_id = ?`,
        [stages[idx].stage_id]
      );

      let newStage = stages[idx].stage_name;
      let newStatus = 'READY';

      if (idx + 1 < stages.length) {
        await conn.query(
          `UPDATE application_stages
           SET stage_status = 'CURRENT', updated_date = NOW()
           WHERE stage_id = ?`,
          [stages[idx + 1].stage_id]
        );

        newStage = stages[idx + 1].stage_name;
        newStatus =
          idx + 1 === stages.length - 1 ? 'READY' : 'IN_REVIEW';
      }

      await conn.query(
        `UPDATE applications
         SET current_stage = ?, status = ?
         WHERE application_id = ?`,
        [newStage, newStatus, id]
      );

      const [[appRow]] = await conn.query(
        'SELECT user_id FROM applications WHERE application_id = ?',
        [id]
      );

      if (appRow) {
        await conn.query(
          'INSERT INTO notifications (user_id, message) VALUES (?, ?)',
          [
            appRow.user_id,
            `Your application #${id} is now: ${newStage}`
          ]
        );
      }

      await conn.commit();

      res.json({
        currentStage: newStage,
        status: newStatus
      });

    } catch (e) {
      if (conn) await conn.rollback();
      res.status(500).json({ error: e.message });

    } finally {
      if (conn) conn.release();
    }
  });

  // ---------- Appointment request ----------
  app.post('/api/appointment-requests', requireLogin, async (req, res) => {
    const { office, date, purpose } = req.body;

    if (!/^\d{4}-\d{2}-\d{2}$/.test(date || '')) {
      return res.status(400).json({
        error: 'Enter the date as YYYY-MM-DD, for example 2026-11-20'
      });
    }

    try {
      const [branches] = await pool.query(
        `SELECT branch_id
         FROM branches
         WHERE branch_name LIKE ? OR city LIKE ?
         LIMIT 1`,
        [`%${office}%`, `%${office}%`]
      );

      if (branches.length === 0) {
        return res.status(400).json({
          error: 'Office not found. Try Johannesburg, Pretoria, Cape Town or Durban'
        });
      }

      const [svc] = await pool.query(
        'SELECT service_id FROM services WHERE service_name LIKE ? LIMIT 1',
        [`%${purpose}%`]
      );

      const serviceId = svc.length
        ? svc[0].service_id
        : 'passport_new';

      await pool.query(
        `INSERT INTO appointments
         (user_id, service_id, branch_id, appointment_datetime)
         VALUES (?, ?, ?, ?)`,
        [
          req.user.userId,
          serviceId,
          branches[0].branch_id,
          date + ' 09:00:00'
        ]
      );

      res.json({ booked: true });

    } catch (e) {
      if (e.code === 'ER_DUP_ENTRY') {
        return res.status(409).json({
          error: 'That slot is already booked. Try another date'
        });
      }

      res.status(400).json({
        error: 'Could not book that date. Check it is a real date',
        details: e.message
      });
    }
  });

};
