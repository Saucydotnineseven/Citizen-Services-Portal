require('dotenv').config();
const express = require('express');
const cors = require('cors');
const path = require('path');
const pool = require('./db');
const migrateAdmin = require('./migrate-admin');
const app = express();
app.disable('x-powered-by');

const allowedOrigins = new Set([
  'https://citizen-services-portal.onrender.com'
]);

app.use(cors({
  origin(origin, callback) {
    if (!origin || allowedOrigins.has(origin)) return callback(null, true);
    return callback(null, false);
  },
  methods: ['GET', 'POST', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));
app.use(express.json({ limit: '50kb' }));
app.use('/api', (req, res, next) => {
  res.set('Cache-Control', 'no-store');
  next();
});

const publicDir = path.join(__dirname, 'public');

app.get('/', (req, res) => {
  res.sendFile(path.join(publicDir, 'index.html'));
});

app.get('/demo', (req, res) => {
  res.sendFile(path.join(publicDir, 'demo.html'));
});

app.get('/download/app.apk', (req, res) => {
  res.download(path.join(publicDir, 'downloads', 'Home-Affairs-Citizen-Services-Demo.apk'));
});

require('./dbroutes')(app); // plug in all the endpoints

async function startServer() {
  // Apply the additive account-management migration before accepting requests.
  await migrateAdmin(pool);

  const bootstrapEmail = String(process.env.ADMIN_BOOTSTRAP_EMAIL || '')
    .trim()
    .toLowerCase();
  if (bootstrapEmail) {
    const [rows] = await pool.query(
      `SELECT user_id, role FROM users
       WHERE LOWER(email) = ? AND account_status = 'ACTIVE'`,
      [bootstrapEmail]
    );
    if (rows.length !== 1) {
      throw new Error('ADMIN_BOOTSTRAP_EMAIL must match one existing active account');
    }
    if (rows[0].role !== 'ADMIN') {
      await pool.query(
        "UPDATE users SET role = 'ADMIN' WHERE user_id = ?",
        [rows[0].user_id]
      );
      console.log('Configured the bootstrap administrator account');
    }
  }

  const PORT = process.env.PORT || 3000;
  // '0.0.0.0' lets other devices (your phone) reach the server
  app.listen(PORT, '0.0.0.0', () => console.log('API running on port ' + PORT));
}

startServer().catch(async error => {
  console.error('API startup failed:', error.code || error.message || 'UNKNOWN');
  await pool.end();
  process.exit(1);
});
