// Run ONCE:  node setup-db.js
// Creates all the tables and loads the services and branches.
require('dotenv').config();
const fs = require('fs');
const mysql = require('mysql2/promise');

(async () => {
  const conn = await mysql.createConnection({
    host: process.env.DB_HOST,
    port: Number(process.env.DB_PORT),
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME,
    ssl: { ca: process.env.DB_CA || fs.readFileSync(process.env.DB_CA_FILE || 'ca.pem', 'utf8') },
    multipleStatements: true   // lets us run the whole schema.sql file in one go
  });

  await conn.query(fs.readFileSync('schema.sql', 'utf8'));
  console.log('Tables and seed data created successfully');
  await conn.end();
})().catch(err => {
  console.error('Setup failed:', err.message);
  process.exit(1);
});