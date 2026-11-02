
// All API endpoints for the Home Affairs Assistant App.

const pool = require('./db');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { loginIpLimit, loginAccountLimit, registrationLimit } = require('./security');

function sendServerError(req, res, error) {
  console.error('API request failed', {
    method: req.method,
    path: req.path,
    code: error && error.code ? error.code : 'UNKNOWN'
  });
  return res.status(500).json({
    error: 'The request could not be completed. Please try again later.'
  });
}

// Recheck the account state and role on every protected request. This makes
// suspension and role changes take effect immediately, even for old tokens.
async function requireLogin(req, res, next) {
  const authorization = req.get('authorization') || '';
  const match = /^Bearer ([^\s]+)$/.exec(authorization);

  if (!match || !process.env.JWT_SECRET) {
    return res.status(401).json({ error: 'Please log in' });
  }

  try {
    const claims = jwt.verify(match[1], process.env.JWT_SECRET, {
      algorithms: ['HS256']
    });
    const [rows] = await pool.query(
      'SELECT user_id, role, account_status FROM users WHERE user_id = ?',
      [claims.userId]
    );

    if (!rows.length || rows[0].account_status !== 'ACTIVE') {
      return res.status(401).json({ error: 'Please log in' });
    }

    req.user = { userId: rows[0].user_id, role: rows[0].role };
    res.set('Cache-Control', 'no-store');
    return next();
  } catch (e) {
    if (e.name === 'JsonWebTokenError' || e.name === 'TokenExpiredError') {
      return res.status(401).json({ error: 'Please log in' });
    }
    return sendServerError(req, res, e);
  }
}

// Only allows OFFICIAL / ADMIN accounts through
function requireStaff(req, res, next) {
  if (req.user.role !== 'OFFICIAL' && req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Officials only' });
  }

  next();
}

function requireAdmin(req, res, next) {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Administrators only' });
  }
  return next();
}

function maskId(value) {
  const id = String(value || '');
  return id.length > 4 ? `${'*'.repeat(id.length - 4)}${id.slice(-4)}` : id;
}

function maskEmail(value) {
  const [name, domain] = String(value || '').split('@');
  if (!domain) return '';
  return `${name.slice(0, 1)}***@${domain}`;
}

module.exports = function (app) {

  // ---------- Database health ----------
  app.get('/api/health/db', async (req, res) => {
    try {
      await pool.query('SELECT 1');
      res.json({ database: 'connected' });
    } catch (e) {
      sendServerError(req, res, e);
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
      return sendServerError(req, res, e);
    }
  });

  // ---------- Register ----------
  app.post('/api/register', registrationLimit, async (req, res) => {
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

      return sendServerError(req, res, e);
    }
  });

  // ---------- Login ----------
  app.post('/api/login', loginIpLimit, loginAccountLimit, async (req, res) => {
    try {
      const { idNumber, email, password } = req.body;
      const who = idNumber || email || '';

      const [rows] = await pool.query(
        `SELECT user_id, first_name, role, password_hash, account_status
         FROM users WHERE id_number = ? OR email = ?`,
        [who, who]
      );

      const user = rows[0];

      if (!user || user.account_status !== 'ACTIVE'
          || !(await bcrypt.compare(password || '', user.password_hash))) {
        return res.status(401).json({
          error: 'Wrong ID number or password'
        });
      }

      const token = jwt.sign(
        { userId: user.user_id, role: user.role },
        process.env.JWT_SECRET,
        { expiresIn: '2h', algorithm: 'HS256' }
      );

      res.json({
        token,
        userId: user.user_id,
        role: user.role,
        firstName: user.first_name
      });

    } catch (e) {
      return sendServerError(req, res, e);
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
      return sendServerError(req, res, e);
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
      return sendServerError(req, res, e);

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
      return sendServerError(req, res, e);
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
      return sendServerError(req, res, e);
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

      return sendServerError(req, res, e);
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
      return sendServerError(req, res, e);
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
      return sendServerError(req, res, e);
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
      return sendServerError(req, res, e);
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
      return sendServerError(req, res, e);
    }
  });

  // ---------- Official: all applications ----------
  app.get('/api/staff/applications', requireLogin, requireStaff, async (req, res) => {
    const search = String(req.query.search || '').trim().slice(0, 100);
    const term = `%${search}%`;
    try {
      const [rows] = await pool.query(
        `SELECT a.application_id, a.status, a.current_stage, a.submission_date,
                s.service_name,
                u.first_name, u.last_name
         FROM applications a
         JOIN services s ON a.service_id = s.service_id
         JOIN users u ON a.user_id = u.user_id
         WHERE (? = '' OR CAST(a.application_id AS CHAR) LIKE ?
                OR s.service_name LIKE ? OR u.first_name LIKE ?
                OR u.last_name LIKE ? OR CONCAT(u.first_name, ' ', u.last_name) LIKE ?)
         ORDER BY a.submission_date DESC`,
        [search, term, term, term, term, term]
      );

      res.json(rows);

    } catch (e) {
      return sendServerError(req, res, e);
    }
  });

  // ---------- Administrator: user account management ----------
  app.get('/api/admin/users', requireLogin, requireAdmin, async (req, res) => {
    const search = String(req.query.search || '').trim().slice(0, 100);
    const offset = Math.max(0, Number.parseInt(req.query.offset, 10) || 0);
    const limit = 50;
    const term = `%${search}%`;

    try {
      const [rows] = await pool.query(
        `SELECT user_id, first_name, last_name, email, id_number,
                role, account_status, created_at
         FROM users
         WHERE (? = '' OR first_name LIKE ? OR last_name LIKE ?
                OR email LIKE ? OR id_number LIKE ?)
         ORDER BY created_at DESC, user_id DESC
         LIMIT ? OFFSET ?`,
        [search, term, term, term, term, limit + 1, offset]
      );

      const hasMore = rows.length > limit;
      const users = rows.slice(0, limit).map(user => ({
        id: user.user_id,
        firstName: user.first_name,
        lastName: user.last_name,
        emailMasked: maskEmail(user.email),
        idNumberMasked: maskId(user.id_number),
        role: user.role,
        status: user.account_status,
        createdAt: user.created_at
      }));

      res.json({ users, offset, limit, hasMore });
    } catch (e) {
      return sendServerError(req, res, e);
    }
  });

  app.post('/api/admin/users/:id/status', requireLogin, requireAdmin, async (req, res) => {
    const targetId = Number.parseInt(req.params.id, 10);
    const status = req.body && req.body.status;

    if (!Number.isSafeInteger(targetId) || targetId < 1
        || !['ACTIVE', 'SUSPENDED'].includes(status)) {
      return res.status(400).json({ error: 'Invalid account status request' });
    }
    if (targetId === req.user.userId) {
      return res.status(400).json({ error: 'You cannot suspend your own administrator account' });
    }

    let conn;
    try {
      conn = await pool.getConnection();
      await conn.beginTransaction();

      const [rows] = await conn.query(
        'SELECT user_id, role, account_status FROM users WHERE user_id = ? FOR UPDATE',
        [targetId]
      );
      if (!rows.length) {
        await conn.rollback();
        return res.status(404).json({ error: 'Account not found' });
      }
      if (rows[0].role === 'ADMIN') {
        await conn.rollback();
        return res.status(403).json({ error: 'Administrator accounts must be managed through the trusted deployment process' });
      }

      const previousStatus = rows[0].account_status;
      if (previousStatus !== status) {
        await conn.query(
          'UPDATE users SET account_status = ? WHERE user_id = ?',
          [status, targetId]
        );
        await conn.query(
          `INSERT INTO admin_audit_log (actor_user_id, target_user_id, action)
           VALUES (?, ?, ?)`,
          [req.user.userId, targetId, status === 'SUSPENDED' ? 'USER_SUSPENDED' : 'USER_REACTIVATED']
        );
      }

      await conn.commit();
      return res.json({ userId: targetId, status });
    } catch (e) {
      if (conn) await conn.rollback();
      return sendServerError(req, res, e);
    } finally {
      if (conn) conn.release();
    }
  });

  app.get('/api/admin/audit', requireLogin, requireAdmin, async (req, res) => {
    try {
      const [rows] = await pool.query(
        `SELECT l.audit_id, l.action, l.created_at,
                l.actor_user_id, actor.first_name AS actor_first_name,
                actor.last_name AS actor_last_name,
                l.target_user_id, target.first_name AS target_first_name,
                target.last_name AS target_last_name
         FROM admin_audit_log l
         JOIN users actor ON actor.user_id = l.actor_user_id
         LEFT JOIN users target ON target.user_id = l.target_user_id
         ORDER BY l.created_at DESC, l.audit_id DESC
         LIMIT 50`
      );
      res.json(rows);
    } catch (e) {
      return sendServerError(req, res, e);
    }
  });

  // ---------- Official: advance application ----------
  app.post('/api/staff/applications/:id/advance', requireLogin, requireStaff, async (req, res) => {
    const idText = String(req.params.id || '');
    const id = Number(idText);
    if (!/^\d+$/.test(idText) || !Number.isSafeInteger(id) || id < 1) {
      return res.status(400).json({ error: 'Invalid application ID' });
    }
    let conn;

    try {
      conn = await pool.getConnection();
      await conn.beginTransaction();

      const [applications] = await conn.query(
        `SELECT application_id, user_id, current_stage, status
         FROM applications WHERE application_id = ? FOR UPDATE`,
        [id]
      );
      if (!applications.length) {
        await conn.rollback();
        return res.status(404).json({ error: 'Application not found' });
      }
      const application = applications[0];
      if (application.status === 'REJECTED' || application.status === 'READY') {
        await conn.rollback();
        return res.status(409).json({ error: 'This application is already complete' });
      }

      let [stages] = await conn.query(
        `SELECT * FROM application_stages
         WHERE application_id = ?
         ORDER BY stage_order FOR UPDATE`,
        [id]
      );

      const stageNames = [
        'Submitted', 'Documents Verified', 'Under Review',
        'Approved', 'Ready for Collection'
      ];
      let activeIndex = stageNames.findIndex(
        name => name.toLowerCase() === String(application.current_stage || '').trim().toLowerCase()
      );
      if (activeIndex < 0) {
        activeIndex = application.status === 'APPROVED' ? 3
          : application.status === 'IN_REVIEW' ? 2 : 0;
      }

      // Older demo applications may predate application_stages, or have no
      // CURRENT row. Reconstruct the tracking rows from the saved application.
      if (stages.length === 0) {
        for (let i = 0; i < stageNames.length; i++) {
          await conn.query(
            `INSERT INTO application_stages
             (application_id, stage_order, stage_name, stage_status, updated_date)
             VALUES (?, ?, ?, ?, ?)`,
            [id, i + 1, stageNames[i],
              i < activeIndex ? 'DONE' : i === activeIndex ? 'CURRENT' : 'PENDING',
              i <= activeIndex ? new Date() : null]
          );
        }
        [stages] = await conn.query(
          `SELECT * FROM application_stages
           WHERE application_id = ? ORDER BY stage_order FOR UPDATE`,
          [id]
        );
      }

      let idx = stages.findIndex(
        s => s.stage_status === 'CURRENT'
      );

      if (idx === -1) {
        const currentName = String(application.current_stage || '').trim().toLowerCase();
        const storedIndex = stages.findIndex(
          s => String(s.stage_name).trim().toLowerCase() === currentName
        );
        idx = storedIndex >= 0 ? storedIndex : Math.min(activeIndex, stages.length - 1);
        await conn.query(
          `UPDATE application_stages
           SET stage_status = CASE
                 WHEN stage_order < ? THEN 'DONE'
                 WHEN stage_order = ? THEN 'CURRENT'
                 ELSE 'PENDING'
               END,
               updated_date = CASE WHEN stage_order = ? THEN NOW() ELSE updated_date END
           WHERE application_id = ?`,
          [stages[idx].stage_order, stages[idx].stage_order, stages[idx].stage_order, id]
        );
      }

      if (idx === -1) {
        await conn.rollback();
        return res.status(400).json({
          error: 'No application stage is available to advance'
        });
      }
      if (idx + 1 >= stages.length) {
        await conn.rollback();
        return res.status(409).json({ error: 'This application is already at its final stage' });
      }

      const newStage = stages[idx + 1].stage_name;
      const newStatus = newStage === 'Ready for Collection' ? 'READY'
        : newStage === 'Approved' ? 'APPROVED'
          : newStage === 'Submitted' ? 'SUBMITTED' : 'IN_REVIEW';
      await conn.query(
        `UPDATE application_stages
         SET stage_status = CASE
               WHEN stage_order < ? THEN 'DONE'
               WHEN stage_order = ? THEN 'CURRENT'
               ELSE 'PENDING'
             END,
             updated_date = CASE
             WHEN stage_order = ? THEN NOW()
               ELSE updated_date
             END
         WHERE application_id = ?`,
        [stages[idx + 1].stage_order, stages[idx + 1].stage_order,
          stages[idx + 1].stage_order, id]
      );

      await conn.query(
        `UPDATE applications
         SET current_stage = ?, status = ?
         WHERE application_id = ?`,
        [newStage, newStatus, id]
      );

      await conn.query(
        'INSERT INTO notifications (user_id, message) VALUES (?, ?)',
        [application.user_id, `Your application #${id} is now: ${newStage}`]
      );

      await conn.commit();

      res.json({
        currentStage: newStage,
        status: newStatus
      });

    } catch (e) {
      if (conn) await conn.rollback();
      return sendServerError(req, res, e);

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

      return sendServerError(req, res, e);
    }
  });

};
