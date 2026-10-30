-- =========================================================
-- Home Affairs Assistant App: MySQL schema (from our ERD)
-- Safe to run more than once.
-- =========================================================

CREATE TABLE IF NOT EXISTS service_categories (
  category_id   INT PRIMARY KEY,
  category_name VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS services (
  service_id         VARCHAR(50) PRIMARY KEY,
  category_id        INT NOT NULL,
  service_name       VARCHAR(120) NOT NULL,
  description        VARCHAR(255),
  fee                DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (fee >= 0),
  processing_time    VARCHAR(50),
  required_documents JSON,
  required_fields    JSON,
  FOREIGN KEY (category_id) REFERENCES service_categories(category_id)
);

CREATE TABLE IF NOT EXISTS branches (
  branch_id   INT AUTO_INCREMENT PRIMARY KEY,
  branch_name VARCHAR(100) NOT NULL,
  address     VARCHAR(200),
  city        VARCHAR(60),
  province    VARCHAR(40)
);

CREATE TABLE IF NOT EXISTS users (
  user_id       INT AUTO_INCREMENT PRIMARY KEY,
  id_number     VARCHAR(20) UNIQUE,
  first_name    VARCHAR(60) NOT NULL,
  last_name     VARCHAR(60) NOT NULL,
  email         VARCHAR(120) NOT NULL UNIQUE,
  phone         VARCHAR(15),
  password_hash VARCHAR(255) NOT NULL,
  role          ENUM('CITIZEN','FOREIGN_NATIONAL','OFFICIAL','ADMIN') NOT NULL DEFAULT 'CITIZEN',
  face_verified TINYINT(1) NOT NULL DEFAULT 0,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS officials (
  official_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id     INT NOT NULL UNIQUE,
  branch_id   INT NOT NULL,
  FOREIGN KEY (user_id)   REFERENCES users(user_id),
  FOREIGN KEY (branch_id) REFERENCES branches(branch_id)
);

CREATE TABLE IF NOT EXISTS appointments (
  appointment_id       INT AUTO_INCREMENT PRIMARY KEY,
  user_id              INT NOT NULL,
  service_id           VARCHAR(50) NOT NULL,
  branch_id            INT NOT NULL,
  appointment_datetime DATETIME NOT NULL,
  status ENUM('BOOKED','CANCELLED','COMPLETED') NOT NULL DEFAULT 'BOOKED',
  UNIQUE (branch_id, appointment_datetime),
  FOREIGN KEY (user_id)    REFERENCES users(user_id),
  FOREIGN KEY (service_id) REFERENCES services(service_id),
  FOREIGN KEY (branch_id)  REFERENCES branches(branch_id)
);

CREATE TABLE IF NOT EXISTS applications (
  application_id       INT AUTO_INCREMENT PRIMARY KEY,
  user_id              INT NOT NULL,
  service_id           VARCHAR(50) NOT NULL,
  assigned_official_id INT NULL,
  submission_date      DATETIME DEFAULT CURRENT_TIMESTAMP,
  current_stage        VARCHAR(60),
  status ENUM('SUBMITTED','IN_REVIEW','APPROVED','REJECTED','READY') NOT NULL DEFAULT 'SUBMITTED',
  form_data            JSON,
  FOREIGN KEY (user_id)              REFERENCES users(user_id),
  FOREIGN KEY (service_id)           REFERENCES services(service_id),
  FOREIGN KEY (assigned_official_id) REFERENCES officials(official_id)
);

CREATE TABLE IF NOT EXISTS application_stages (
  stage_id       INT AUTO_INCREMENT PRIMARY KEY,
  application_id INT NOT NULL,
  stage_order    INT NOT NULL,
  stage_name     VARCHAR(60) NOT NULL,
  stage_status   ENUM('DONE','CURRENT','PENDING') NOT NULL DEFAULT 'PENDING',
  updated_date   DATETIME NULL,
  FOREIGN KEY (application_id) REFERENCES applications(application_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS documents (
  document_id     INT AUTO_INCREMENT PRIMARY KEY,
  application_id  INT NOT NULL,
  document_type   VARCHAR(80),
  file_path       VARCHAR(255) NOT NULL,
  upload_date     DATETIME DEFAULT CURRENT_TIMESTAMP,
  verified_status TINYINT(1) NOT NULL DEFAULT 0,
  FOREIGN KEY (application_id) REFERENCES applications(application_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payments (
  payment_id     INT AUTO_INCREMENT PRIMARY KEY,
  application_id INT NOT NULL,
  amount         DECIMAL(10,2) NOT NULL CHECK (amount > 0),
  payment_method VARCHAR(30),
  payment_date   DATETIME DEFAULT CURRENT_TIMESTAMP,
  payment_status ENUM('PAID','FAILED','PENDING') NOT NULL DEFAULT 'PENDING',
  FOREIGN KEY (application_id) REFERENCES applications(application_id)
);

CREATE TABLE IF NOT EXISTS notifications (
  notification_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id         INT NOT NULL,
  message         VARCHAR(255) NOT NULL,
  date_sent       DATETIME DEFAULT CURRENT_TIMESTAMP,
  read_status     TINYINT(1) NOT NULL DEFAULT 0,
  FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS appeals (
  appeal_id       INT AUTO_INCREMENT PRIMARY KEY,
  application_id  INT NOT NULL,
  reason          TEXT NOT NULL,
  submission_date DATETIME DEFAULT CURRENT_TIMESTAMP,
  outcome         VARCHAR(50) DEFAULT 'PENDING',
  FOREIGN KEY (application_id) REFERENCES applications(application_id)
);

-- ================= SEED DATA (prototype values, NOT official fees) =================

INSERT IGNORE INTO service_categories (category_id, category_name) VALUES
 (1,'Identity Documents'),(2,'Travel Documents'),(3,'Civic Services'),(4,'Amendments'),
 (5,'Citizenship'),(6,'Visas and Permits'),(7,'Permanent Residence'),(8,'Asylum and Refugee');

INSERT IGNORE INTO services
 (service_id, category_id, service_name, description, fee, processing_time, required_documents, required_fields) VALUES
 ('smart_id_first',1,'Smart ID Card - First Issue','Apply for your first smart ID card.',0,'10-15 working days',
  '["Birth certificate","Parent ID copy","Proof of address"]','["Full name","Date of birth","Place of birth","Home address"]'),
 ('smart_id_replacement',1,'Smart ID Card - Replacement','Replace a lost, stolen or damaged smart ID.',140,'10-15 working days',
  '["Police affidavit","Proof of address"]','["Full name","ID number","Reason for replacement"]'),
 ('temp_id',1,'Temporary Identity Certificate','Short-term proof of identity.',70,'Same day',
  '["Proof of application","Photo"]','["Full name","ID number","Reason"]'),
 ('passport_new',2,'Passport - New Application','Apply for a new South African passport.',600,'10-15 working days',
  '["Smart ID copy","Passport photo","Proof of address"]','["Full name","ID number","Contact number","Home address"]'),
 ('passport_renewal',2,'Passport - Renewal','Renew an expiring passport.',600,'10-15 working days',
  '["Old passport","Smart ID copy","Passport photo"]','["Full name","Passport number","Expiry date"]'),
 ('birth_registration',3,'Birth Registration','Register the birth of a child.',0,'5-10 working days',
  '["Hospital birth notification","Parents ID copies"]','["Child full name","Date of birth","Place of birth","Mother ID number","Father ID number"]'),
 ('birth_late_registration',3,'Late Registration of Birth','Register a birth not registered within 30 days.',0,'30+ working days',
  '["Affidavits","Clinic card","Parents ID copies"]','["Full name","Date of birth","Place of birth","Reason for late registration"]'),
 ('marriage_registration',3,'Marriage Registration','Register a marriage.',0,'5-10 working days',
  '["Both parties ID copies","Marriage officer documents"]','["Spouse 1 full name","Spouse 2 full name","Date of marriage","Place of marriage"]'),
 ('death_registration',3,'Death Registration','Register a death and get a death certificate.',0,'5-10 working days',
  '["Doctor death notification","Deceased ID copy","Informant ID copy"]','["Deceased full name","ID number","Date of death","Place of death"]'),
 ('certificate_request',3,'Certificate Request','Request a birth, marriage or death certificate.',75,'10-15 working days',
  '["ID copy"]','["Full name","ID number","Certificate type"]'),
 ('name_change',4,'Name Change / Record Correction','Correct or change details on your record.',0,'30+ working days',
  '["ID copy","Supporting documents","Affidavit"]','["Current full name","New full name","Reason for change"]'),
 ('citizenship_naturalisation',5,'Naturalisation','Apply for citizenship by naturalisation.',0,'12+ months',
  '["Permanent residence permit","Police clearance","Proof of residence"]','["Full name","Nationality","Permit number","Years in South Africa"]'),
 ('visa_visitor',6,'Visitor Visa','Apply for or extend a visitor visa.',0,'8-12 weeks',
  '["Passport","Proof of funds","Return ticket","Accommodation details"]','["Full name","Passport number","Nationality","Purpose of visit","Arrival date"]'),
 ('visa_study',6,'Study Visa','Apply for a study visa.',0,'8-12 weeks',
  '["Passport","Letter of acceptance","Proof of funds","Medical report"]','["Full name","Passport number","Institution","Course","Start date"]'),
 ('visa_work',6,'Work Visa','Apply for a general or critical skills work visa.',0,'8-12 weeks',
  '["Passport","Employment contract","Qualifications","Police clearance"]','["Full name","Passport number","Employer","Job title","Start date"]'),
 ('permanent_residence',7,'Permanent Residence Application','Apply for permanent residence.',0,'12+ months',
  '["Passport","Police clearance","Proof of status or relationship"]','["Full name","Passport number","Basis for application","Years in South Africa"]'),
 ('asylum_renewal',8,'Asylum Seeker Permit Renewal','Renew your asylum seeker permit.',0,'Varies',
  '["Current permit","Photo"]','["Full name","Permit number","Expiry date","Contact number"]');

INSERT IGNORE INTO branches (branch_id, branch_name, address, city, province) VALUES
 (1,'Johannesburg Branch','Sample Street 1','Johannesburg','Gauteng'),
 (2,'Pretoria Branch','Sample Street 2','Pretoria','Gauteng'),
 (3,'Cape Town Branch','Sample Street 3','Cape Town','Western Cape'),
 (4,'Durban Branch','Sample Street 4','Durban','KwaZulu-Natal');