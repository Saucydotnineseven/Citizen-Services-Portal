require('dotenv').config();
const express = require('express');
const cors = require('cors');
const app = express();
app.use(cors());            // allow requests from other origins
app.use(express.json());    // read JSON request bodies

require('./dbRoutes')(app); // plug in all the endpoints

const PORT = process.env.PORT || 3000;
// '0.0.0.0' lets other devices (your phone) reach the server
app.listen(PORT, '0.0.0.0', () => console.log('API running on port ' + PORT));