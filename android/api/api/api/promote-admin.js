require('dotenv').config();
const pool = require('./db');

(async () => {
  const email = String(process.argv[2] || '').trim().toLowerCase();
  if (!email || !email.includes('@')) {
    throw new Error('Pass the email of an existing, trusted staff account.');
  }

  const [result] = await pool.query(
    "UPDATE users SET role = 'ADMIN' WHERE LOWER(email) = ? AND account_status = 'ACTIVE'",
    [email]
  );

  if (result.affectedRows !== 1) {
    throw new Error('No active account matched, or the account is already an admin.');
  }

  console.log('Existing account promoted to ADMIN.');
  await pool.end();
})().catch(async error => {
  console.error('Admin promotion failed:', error.message);
  await pool.end();
  process.exit(1);
});
