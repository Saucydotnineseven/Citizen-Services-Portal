const { createHash } = require('crypto');

function clientIp(req) {
  // Render documents X-Forwarded-For as the source for the original client IP.
  const forwarded = req.get('x-forwarded-for');
  const ip = forwarded ? forwarded.split(',')[0].trim() : req.socket.remoteAddress;
  return (ip || 'unknown').slice(0, 64);
}

function identifierHash(req) {
  const identifier = String(req.body?.idNumber || req.body?.email || '')
    .trim()
    .toLowerCase();
  return createHash('sha256').update(identifier).digest('hex');
}

function rateLimit({ windowMs, max, key, message }) {
  const buckets = new Map();

  return (req, res, next) => {
    const now = Date.now();
    const bucketKey = key(req);
    let bucket = buckets.get(bucketKey);

    if (!bucket || bucket.resetAt <= now) {
      if (buckets.size >= 50000) {
        const oldestKey = buckets.keys().next().value;
        if (oldestKey !== undefined) buckets.delete(oldestKey);
      }
      bucket = { count: 0, resetAt: now + windowMs };
      buckets.set(bucketKey, bucket);
    }

    if (bucket.count >= max) {
      res.set('Retry-After', String(Math.max(1, Math.ceil((bucket.resetAt - now) / 1000))));
      return res.status(429).json({ error: message });
    }

    bucket.count += 1;
    return next();
  };
}

const loginIpLimit = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 60,
  key: clientIp,
  message: 'Too many sign-in attempts. Please wait and try again.'
});

const loginAccountLimit = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  key: req => `${clientIp(req)}:${identifierHash(req)}`,
  message: 'Too many sign-in attempts. Please wait and try again.'
});

const registrationLimit = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 10,
  key: clientIp,
  message: 'Too many account creation attempts. Please try again later.'
});

module.exports = { loginIpLimit, loginAccountLimit, registrationLimit };
