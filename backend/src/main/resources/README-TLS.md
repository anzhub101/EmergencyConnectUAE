# TLS / HTTPS (self-signed certificate)

The backend serves HTTPS using a self-signed certificate stored in `keystore.p12`.

Configured in `application.yml` under `server.ssl`. Defaults:

| Setting              | Env var                 | Default                  |
|----------------------|-------------------------|--------------------------|
| Port                 | `SERVER_PORT`           | `8443`                   |
| SSL enabled          | `SSL_ENABLED`           | `true`                   |
| Keystore             | `SSL_KEYSTORE`          | `classpath:keystore.p12` |
| Keystore password    | `SSL_KEYSTORE_PASSWORD` | `changeit`               |
| Key alias            | `SSL_KEY_ALIAS`         | `emergencyconnect`       |

The app is reachable at `https://localhost:8443`.

## Regenerating the certificate

The bundled cert is valid for 10 years from generation. To recreate it:

```bash
cd backend/src/main/resources
keytool -genkeypair \
  -alias emergencyconnect \
  -keyalg RSA -keysize 2048 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -validity 3650 \
  -storepass changeit \
  -dname "CN=localhost, OU=EmergencyConnectUAE, O=ADU, L=AbuDhabi, ST=AbuDhabi, C=AE" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"
```

## Notes

- Because the certificate is self-signed, browsers and HTTP clients will warn
  about an untrusted certificate. In dev this is expected — accept the warning,
  or pass `-k`/`--insecure` to `curl`.
- The Vite dev proxy (`frontend/vite.config.ts`) targets `https://localhost:8443`
  with `secure: false` so it accepts the self-signed cert.
- For production, replace this keystore with a CA-issued certificate and supply
  the keystore path/password via the environment variables above. Do not ship
  the `changeit` password.
```
