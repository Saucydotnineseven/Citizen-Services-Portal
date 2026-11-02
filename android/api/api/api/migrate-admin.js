require('dotenv').config();
const pool = require('./db');

async function migrateAdmin(db = pool) {
  const [columns] = await db.query(
    `SELECT COLUMN_NAME
     FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'users'
       AND COLUMN_NAME = 'account_status'`
  );

  if (columns.length === 0) {
    await db.query(
      "ALTER TABLE users ADD COLUMN account_status ENUM('ACTIVE','SUSPENDED') NOT NULL DEFAULT 'ACTIVE'"
    );
    console.log('Added users.account_status');
  } else {
    console.log('users.account_status already exists');
  }

  await db.query(`
    CREATE TABLE IF NOT EXISTS admin_audit_log (
      audit_id INT AUTO_INCREMENT PRIMARY KEY,
      actor_user_id INT NOT NULL,
      target_user_id INT NULL,
      action VARCHAR(40) NOT NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (actor_user_id) REFERENCES users(user_id),
      FOREIGN KEY (target_user_id) REFERENCES users(user_id) ON DELETE SET NULL
    )
  `);
  console.log('Admin account-management migration complete');
}

module.exports = migrateAdmin;

if (require.main === module) {
  migrateAdmin()
    .then(() => pool.end())
    .catch(async error => {
      console.error('Admin migration failed:', error.code || 'UNKNOWN');
      await pool.end();
      process.exit(1);
    });
}
