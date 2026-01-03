const express = require('express');
const { Kafka } = require('kafkajs');
const nodemailer = require('nodemailer');
const winston = require('winston');
const promClient = require('prom-client');

// Logger setup
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.combine(winston.format.timestamp(), winston.format.json()),
  transports: [new winston.transports.Console()]
});

// Prometheus metrics
const register = new promClient.Registry();
promClient.collectDefaultMetrics({ register });
const notificationsSent = new promClient.Counter({
  name: 'notifications_sent_total',
  help: 'Total notifications sent',
  labelNames: ['type', 'channel']
});
register.registerMetric(notificationsSent);

const app = express();
app.use(express.json());

app.get('/health', (req, res) => res.json({ status: 'healthy' }));
app.get('/metrics', async (req, res) => {
  res.set('Content-Type', register.contentType);
  res.end(await register.metrics());
});

// Kafka setup
const kafka = new Kafka({
  clientId: 'notification-service',
  brokers: (process.env.KAFKA_BROKERS || 'localhost:9092').split(',')
});

const consumer = kafka.consumer({ groupId: 'notification-service' });

// Email transporter
const emailTransporter = nodemailer.createTransport({
  host: process.env.SMTP_HOST || 'localhost',
  port: 587,
  auth: { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS }
});

// Email templates
const templates = {
  TRANSACTION_COMPLETED: (data) => `
    <h2>Transaction Completed</h2>
    <p>Your transaction has been processed successfully.</p>
    <p><strong>Reference:</strong> ${data.referenceNumber}</p>
    <p><strong>Amount:</strong> ${data.currency} ${data.amount}</p>
  `,
  TRANSACTION_FAILED: (data) => `
    <h2>Transaction Failed</h2>
    <p>Reference: ${data.referenceNumber}</p>
    <p>Reason: ${data.reason}</p>
  `,
  FRAUD_ALERT: (data) => `
    <h2>⚠️ Security Alert</h2>
    <p>Suspicious activity detected on your account.</p>
    <p>Please contact us immediately if you did not authorize this.</p>
  `,
  LOGIN_ALERT: (data) => `
    <h2>New Login Detected</h2>
    <p>Time: ${data.timestamp}</p>
    <p>Location: ${data.location}</p>
  `
};

async function sendEmail(notification) {
  const template = templates[notification.type] || templates.TRANSACTION_COMPLETED;
  await emailTransporter.sendMail({
    from: '"SecureBank" <noreply@securebank.com>',
    to: notification.email,
    subject: notification.title,
    html: template(notification)
  });
  logger.info('Email sent', { to: notification.email });
  notificationsSent.inc({ type: notification.type, channel: 'email' });
}

async function processNotification(message) {
  const notification = JSON.parse(message.value.toString());
  logger.info('Processing notification', { type: notification.type });
  
  try {
    if (notification.email) await sendEmail(notification);
    // Add SMS, Push notification handlers here
  } catch (error) {
    logger.error('Notification failed', { error: error.message });
  }
}

async function startConsumer() {
  await consumer.connect();
  await consumer.subscribe({ topics: ['notifications', 'fraud-alerts'], fromBeginning: false });
  
  await consumer.run({
    eachMessage: async ({ topic, message }) => {
      await processNotification(message);
    }
  });
  logger.info('Kafka consumer started');
}

const PORT = process.env.PORT || 3001;
app.listen(PORT, () => {
  logger.info(`Notification service running on port ${PORT}`);
  startConsumer().catch(err => logger.error('Consumer failed', { error: err.message }));
});
