require('dotenv').config();
const express = require('express');
const cors = require('cors');
const path = require('path');
const app = express();
app.use(cors());            // allow requests from other origins
app.use(express.json());    // read JSON request bodies

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

const PORT = process.env.PORT || 3000;
// '0.0.0.0' lets other devices (your phone) reach the server
app.listen(PORT, '0.0.0.0', () => console.log('API running on port ' + PORT));
