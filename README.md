# 🚀 AI Feed Engine

> **India ka AI-powered short video platform** — TikTok + Instagram + YouTube ka best combination, Spring Boot se bana hua.

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?style=flat-square)
![Redis](https://img.shields.io/badge/Redis-7-red?style=flat-square)
![Kafka](https://img.shields.io/badge/Apache_Kafka-3.x-black?style=flat-square)

---

## 📋 Table of Contents

- [Project Overview](#project-overview)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Features](#features)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Setup & Installation](#setup--installation)
- [Environment Variables](#environment-variables)
- [Running the App](#running-the-app)
- [API Endpoints](#api-endpoints)
- [Database Schema](#database-schema)
- [Key Flows](#key-flows)
- [PWA Support](#pwa-support)
- [Admin Panel](#admin-panel)
- [Deployment](#deployment)

---

## 🎯 Project Overview

AI Feed Engine ek full-stack social media platform hai jisme short video feed, real-time chat, live streaming, creator monetization, aur AI-powered recommendations sab kuch hai.

**Kya kya hai is project mein:**
- Short video feed (Reels style)
- AI-powered 3-layer recommendation system
- Real-time direct messaging (WebSocket/STOMP)
- Live streaming with chat
- Creator wallet & Razorpay payments
- Google OAuth2 + Email/OTP login
- 2FA (Two-Factor Authentication)
- Full Admin Panel with analytics
- PWA support (installable on phone)
- Push notifications (VAPID Web Push)

---

## 🛠 Tech Stack

### Backend
| Technology       | Version |     Use |
|---               |---      |---      |
| Java             |17+      | Main language |
| Spring Boot      | 3.x     | Web framework |
| Spring Security  | 6.x     | Auth & authorization |
| Spring Data JPA  | 3.x     | Database ORM |
| Spring Kafka     | 3.x     | Async event processing |
| Spring WebSocket | 3.x     | Real-time chat |
| Thymeleaf        | 3.x     | Server-side HTML templates |
| Lombok           | latest  | Boilerplate reduction |
| JJWT             | 0.11.x  | JWT token generation |

### Database & Cache
| Technology    | Use |
|---            |---  |
| PostgreSQL 15 | Primary database |
| Redis 7       | Feed cache, rate limiting, OTP, sessions |
| Apache Kafka | Video processing pipeline, engagement events |

### External Services
| Service            | Use |
|---                 |---|
| Google OAuth2      | Social login |
| Zoho Mail / Gmail  | Email sending (OTP, verify, reset) |
| Razorpay           | Payment gateway for creator tips |
| OpenAI API         | AI-powered content features |
| FFmpeg             | Video transcoding & HLS generation |
| VAPID              | Web Push notifications |

### Frontend
| Technology         | Use |
|---                 |---|
| HTML5 + CSS3       | UI templates (Thymeleaf) |
| Vanilla JavaScript | Frontend logic |
| Bootstrap 5        | UI components |
| STOMP.js + SockJS  | WebSocket client |
| Service Worker     | PWA & offline support |

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Browser / PWA                     │
│          HTML + CSS + JS (Thymeleaf render)          │
└───────────────────────┬─────────────────────────────┘
                        │ HTTP / WebSocket
┌───────────────────────▼───────────────────────────────┐
│              Spring Boot Application                  │
│                                                       │
│  IpBanFilter → JwtAuthFilter → Spring Security        │
│                                                       │
│  Controllers (34) → Services (32) → Repositories      │
└────┬────────────────┬──────────────┬───────────────────┘
     │                │              │
┌────▼──────┐    ┌─────▼─────┐     ┌───▼────┐
│PostgreSQL │    │   Redis   │     │  Kafka │
│ (main DB) │    │ (cache)   │     │(events)│
└───────────┘    └───────────┘     └────────┘
                                         │
                                  ┌──────▼──────┐
                                  │VideoProcessing│
                                  │  (FFmpeg)    │
                                  └─────────────┘
```

### Feed Algorithm (3 Layers)
```
User Request
    │
    ├── Layer 1: FOLLOWING (40%) — posts from followed users
    ├── Layer 2: INTEREST  (35%) — tags + trending match
    └── Layer 3: EXPLORE   (25%) — cold start / global trending
                │
         FeedRankingService
         score = likes×2 + comments×3 + watchTime×5
                │
         Creator Diversity Filter (max 5 per creator)
                │
         Redis ZSet (sharded by userId % 32)
```

---

## ✨ Features

### 👤 Auth & Account
- Email + Password registration with email verification (OTP or link)
- Google OAuth2 login
- JWT-based API authentication
- Session-based web authentication
- Two-Factor Authentication (2FA) via email OTP
- Forgot password (reset link, 30 min expiry)
- Change password
- Change email (verification required)
- Account delete (password confirmation)
- IP ban + brute force protection (5 attempts → 15 min lockout)
- Rate limiting (200 req/min per IP)

### 📱 Feed & Video
- AI-powered 3-layer hybrid feed
- HLS video streaming (FFmpeg transcoding)
- Real-time upload progress (SSE)
- Video thumbnail auto-generation
- Reel/short video player
- Hashtag system
- Video bookmarks/saves

### 👥 Social
- Follow / Unfollow
- Block / Unblock users
- Report users & posts
- Comments (nested)
- Likes / Engagement tracking
- Stories (24hr expiry with view tracking)
- Suggested users
- Search (users + posts + hashtags)
- Share panel

### 💬 Real-time
- Direct Messages (WebSocket/STOMP)
- Online presence tracking (Redis)
- Live streaming (start/join/chat/stop)
- In-app notification bell
- Web Push notifications (VAPID)
- Real-time admin notifications broadcast

### 💰 Monetization
- Creator tips via Razorpay
- Creator wallet with earnings tracking
- Withdrawal requests
- Earnings transaction history

### 📊 Analytics
- Creator analytics dashboard
- View counts, watch time, engagement rate
- Follower growth tracking
- Weekly email digest

### 🛡️ Admin Panel
- User management (ban, warn, promote, delete)
- Content moderation (reports, comments, posts)
- IP ban management
- Role management (ADMIN, MODERATOR, ANALYST)
- Analytics & heatmaps
- A/B testing panel
- Audit logs
- Export data
- Admin notifications broadcast
- Banned words filter
- Login history
- Scheduled tasks
- System monitor

### ⚙️ Infrastructure
- PWA (Progressive Web App) — installable on phone
- Service Worker with offline fallback
- Kafka Dead Letter Topic (retry + DLT)
- Redis feed sharding (userId % 32)
- Trending score decay (every 60s)
- Frontend in-memory cache (TTL-based)
- Lazy image loading
- Network-aware video loading
- Responsive design (mobile first)

---

## 📁 Project Structure

```
src/main/
├── java/com/yogesh/
│   ├── AiFeedEngineApplication.java       # Entry point
│   ├── config/                            # Configuration
│   │   ├── CacheConfig.java               # Redis cache config
│   │   ├── KafkaConfig.java               # Kafka producers/consumers
│   │   ├── KafkaErrorConfig.java          # Retry + Dead Letter Topic
│   │   ├── MailConfig.java                # Zoho/Gmail mail senders
│   │   ├── RedisConfig.java               # Redis templates
│   │   ├── SecurityBeans.java             # PasswordEncoder, AuthManager
│   │   ├── SecurityConfig.java            # Spring Security filter chain
│   │   ├── ThymeleafConfig.java           # Template resolvers
│   │   ├── UploadInitializer.java         # Create upload folders on start
│   │   ├── WebConfig.java                 # CORS + static resource caching
│   │   └── WebSocketConfig.java           # STOMP WebSocket config
│   │
│   ├── controller/                        # REST Controllers (34 total)
│   │   ├── AccountController.java         # Account settings, OTP, 2FA
│   │   ├── FeedController.java            # Feed API
│   │   ├── PostController.java            # Post CRUD
│   │   ├── UploadController.java          # Video upload + SSE progress
│   │   ├── VideoController.java           # Legacy video upload
│   │   ├── VideoStreamingController.java  # HLS streaming
│   │   ├── EngagementController.java      # Like/watch/comment
│   │   ├── FollowController.java          # Follow/unfollow
│   │   ├── CommentController.java         # Comments
│   │   ├── StoryController.java           # Stories
│   │   ├── DirectMessageController.java   # DM REST API
│   │   ├── ChatSocketController.java      # WebSocket chat
│   │   ├── LiveStreamController.java      # Live streaming
│   │   ├── MonetizationController.java    # Creator wallet
│   │   ├── RazorpayController.java        # Payment webhook
│   │   ├── NotificationController.java    # Notifications
│   │   ├── PushController.java            # Web Push subscribe
│   │   ├── SearchApiController.java       # Search API
│   │   ├── AnalyticsController.java       # Analytics data
│   │   ├── ProfileController.java         # Profile API
│   │   ├── HashtagController.java         # Hashtag feed
│   │   ├── AiController.java              # AI features
│   │   └── ...more
│   │
│   ├── logincontroller/
│   │   ├── AuthController.java            # Register, Login, 2FA verify
│   │   └── AdminController.java           # Admin panel (89KB!)
│   │
│   ├── service/                           # Business Logic (32 services)
│   │   ├── AccountService.java            # Auth flows, OTP, 2FA
│   │   ├── EmailService.java              # All email sending
│   │   ├── FeedService.java               # 3-layer feed algorithm
│   │   ├── FeedRankingService.java        # Post scoring
│   │   ├── FeedRefillService.java         # Redis feed refill
│   │   ├── ColdStartService.java          # New user fallback
│   │   ├── EngagementService.java         # Engagement processing
│   │   ├── VideoProcessingService.java    # FFmpeg HLS transcode
│   │   ├── FanoutService.java             # Push to followers
│   │   ├── DirectMessageService.java      # DM logic
│   │   ├── MonetizationService.java       # Wallet & earnings
│   │   ├── RazorpayService.java           # Payment processing
│   │   ├── OpenAiService.java             # AI integration
│   │   ├── UserEmbeddingService.java      # Interest vectors
│   │   ├── VectorSearchService.java       # Cosine similarity
│   │   ├── CollaborativeService.java      # Collaborative filtering
│   │   ├── PushNotificationService.java   # VAPID web push
│   │   ├── StoryService.java              # 24hr stories
│   │   └── ...more
│   │
│   ├── model/                             # JPA Entities (23 models)
│   │   ├── User.java
│   │   ├── Post.java
│   │   ├── Comment.java
│   │   ├── Follow.java
│   │   ├── Engagement.java
│   │   ├── Story.java / StoryView.java
│   │   ├── DirectMessage.java
│   │   ├── LiveStream.java
│   │   ├── Notification.java
│   │   ├── CreatorWallet.java
│   │   ├── EarningTransaction.java
│   │   ├── WithdrawalRequest.java
│   │   ├── EmailOtpToken.java
│   │   ├── EmailVerificationToken.java
│   │   ├── PasswordResetToken.java
│   │   └── ...more
│   │
│   ├── security/
│   │   ├── CustomUserDetails.java
│   │   ├── CustomUserDetailsService.java
│   │   ├── IpBanFilter.java               # IP ban + rate limiting
│   │   ├── JwtAuthFilter.java             # JWT validation
│   │   ├── OAuth2SuccessHandler.java      # Google login
│   │   └── RoleBasedSuccessHandler.java   # Role-based redirect
│   │
│   ├── util/
│   │   ├── FFmpegUtil.java                # Video transcoding
│   │   ├── FileStorageUtil.java           # File paths & folders
│   │   ├── JwtUtil.java                   # JWT generate/validate
│   │   ├── ScoreUtil.java                 # Feed score encoding
│   │   ├── VectorUtil.java                # Cosine similarity
│   │   └── VapidKeyGenerator.java         # Push notification keys
│   │
│   ├── worker/
│   │   ├── FeedWorker.java                # Kafka → Redis ZSet
│   │   └── TrendingWorker.java            # Trending snapshot rebuild
│   │
│   └── scheduler/
│       └── TrendingDecayScheduler.java    # Score decay every 60s
│
└── resources/
    ├── application.yml                    # Dev config
    ├── application-prod.yml               # Prod config
    ├── static/
    │   ├── css/                           # Stylesheets
    │   ├── js/                            # Frontend JS (25 files)
    │   │   ├── sw.js                      # Service Worker (PWA)
    │   │   ├── reels.js                   # Video feed player
    │   │   ├── messages.js                # DM frontend
    │   │   ├── realtime.js                # WebSocket client
    │   │   ├── cacheService.js            # In-memory TTL cache
    │   │   ├── performance.js             # Lazy loading, debounce
    │   │   └── ...more
    │   └── manifest.json                  # PWA manifest
    └── structure/                         # Thymeleaf HTML pages (51 files)
        ├── login.html
        ├── register.html
        ├── reels.html                     # Main feed page
        ├── profile.html
        ├── messages.html
        ├── upload.html
        ├── analytics.html
        ├── monetization.html
        ├── admin-dashboard.html
        └── ...more
```

---

## 📦 Prerequisites

Ye sab install hona chahiye pehle:

```bash
# Java 17+
java -version

# Maven
mvn -version

# PostgreSQL 15
psql --version

# Redis 7
redis-server --version

# Apache Kafka 3.x (with Zookeeper)
kafka-server-start.sh --version

# FFmpeg (video processing ke liye)
ffmpeg -version
```

---

## ⚙️ Setup & Installation

### 1. Repository clone karo

```bash
git clone https://github.com/yourusername/ai-feed-engine.git
cd ai-feed-engine
```

### 2. PostgreSQL database banao

```sql
CREATE DATABASE ai_feed_engine;
CREATE USER postgres WITH PASSWORD 'postgres';
GRANT ALL PRIVILEGES ON DATABASE ai_feed_engine TO postgres;
```

### 3. Redis start karo

```bash
redis-server
# ya Docker se:
docker run -d -p 6379:6379 redis:7
```

### 4. Kafka start karo

```bash
# Zookeeper pehle
bin/zookeeper-server-start.sh config/zookeeper.properties

# Phir Kafka
bin/kafka-server-start.sh config/server.properties

# Required topics banao
bin/kafka-topics.sh --create --topic video-processing --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic engagement-events --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic feed-topic --bootstrap-server localhost:9092
```

### 5. Environment variables set karo

```bash
# Linux/Mac
export MAIL_ZOHO_USERNAME=yourmail@zohomail.com
export MAIL_ZOHO_PASSWORD=yourpassword
export JWT_SECRET=YourSuperSecretKeyAtLeast256BitsLongForHS256Algorithm
export RAZORPAY_KEY_ID=rzp_test_xxxx
export RAZORPAY_KEY_SECRET=xxxx

# Windows (PowerShell)
$env:MAIL_ZOHO_USERNAME="yourmail@zohomail.com"
$env:MAIL_ZOHO_PASSWORD="yourpassword"
```

### 6. VAPID keys generate karo (push notifications ke liye)

```bash
# App start karne ke baad ek baar ye call karo:
curl http://localhost:8080/api/push/generate-keys
# Output mein jo keys aayein unhe application.yml mein daalo
```

### 7. Google OAuth setup (optional)

1. [Google Cloud Console](https://console.cloud.google.com/) pe jaao
2. New project banao
3. OAuth 2.0 Client ID create karo
4. Authorized redirect URI add karo: `http://localhost:8080/login/oauth2/code/google`
5. `application.yml` mein client-id aur client-secret daalo

### 8. Build & run

```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

App `http://localhost:8080` pe available hogi.

---

## 🔐 Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `MAIL_ZOHO_USERNAME` | Yes | — | Zoho mail username |
| `MAIL_ZOHO_PASSWORD` | Yes | — | Zoho mail password |
| `MAIL_GMAIL_USERNAME` | No | — | Gmail username (backup) |
| `MAIL_GMAIL_PASSWORD` | No | — | Gmail password |
| `JWT_SECRET` | Yes | dev-key | JWT signing secret (min 32 chars) |
| `RAZORPAY_KEY_ID` | No | test-key | Razorpay key |
| `RAZORPAY_KEY_SECRET` | No | test-secret | Razorpay secret |
| `VAPID_PUBLIC_KEY` | No | — | Web push public key |
| `VAPID_PRIVATE_KEY` | No | — | Web push private key |
| `APP_BASE_URL` | No | localhost:8080 | App base URL (emails mein use hota) |
| `UPLOAD_PATH` | No | uploads/ | File upload directory |

> ⚠️ **IMPORTANT:** `application.yml` mein kabhi real passwords mat daalo. Hamesha environment variables use karo ya `.env` file banao aur `.gitignore` mein add karo.

---

## ▶️ Running the App

### Development

```bash
mvn spring-boot:run
```

### Production profile

```bash
mvn spring-boot:run -Dspring.profiles.active=prod
# ya
java -jar target/ai-feed-engine.jar --spring.profiles.active=prod
```

### Docker (optional)

```dockerfile
FROM openjdk:17-jre-slim
RUN apt-get update && apt-get install -y ffmpeg
COPY target/ai-feed-engine.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
docker build -t ai-feed-engine .
docker run -p 8080:8080 \
  -e MAIL_ZOHO_USERNAME=your@mail.com \
  -e MAIL_ZOHO_PASSWORD=yourpass \
  -e JWT_SECRET=yoursecret \
  ai-feed-engine
```

---

## 🔌 API Endpoints

### Auth (Public)
```
POST /api/auth/register          # Register (email/OTP choice)
POST /api/auth/login             # Login (returns require2fa flag)
POST /api/auth/verify-2fa        # 2FA OTP verify → session set
POST /api/account/send-otp       # Send OTP
POST /api/account/verify-otp     # Verify OTP
POST /api/account/forgot-password
POST /api/account/reset-password
```

### Account (Authenticated)
```
GET  /account/settings           # Settings page
POST /api/account/change-password
POST /api/account/update-profile
POST /api/account/request-email-change
POST /api/account/2fa/toggle
POST /api/account/delete
```

### Feed
```
GET  /api/feed/hybrid?cursor=&size=    # Main feed
GET  /api/mobile/feed                  # Mobile API feed
GET  /api/mobile/trending              # Trending
```

### Posts & Video
```
POST /api/upload/video                 # Upload with SSE progress
GET  /api/upload/status/{id}           # Upload status
POST /api/post/upload                  # Simple upload
GET  /stream/{postId}/playlist.m3u8   # HLS stream
POST /api/engagement                   # Like/watch/comment
```

### Social
```
POST /api/follow/{userId}
DELETE /api/unfollow/{userId}
GET  /api/followers/{userId}
GET  /api/following/{userId}
POST /api/block/{userId}
POST /api/report
GET  /api/bookmarks
POST /api/bookmarks/{postId}
GET  /api/comments/{postId}
POST /api/comments/{postId}
GET  /api/stories
POST /api/stories
GET  /api/suggestions
```

### Messages & Live
```
GET  /api/dm/conversations
GET  /api/dm/messages/{userId}
WS   /ws (STOMP)                       # WebSocket
     → /app/chat.send
     ← /topic/messages/{userId}
GET  /api/live/active
POST /api/live/start
POST /api/live/stop/{id}
```

### Search & Hashtags
```
GET  /api/search?q=&type=
GET  /api/hashtags/trending
GET  /api/hashtags/{tag}/posts
```

### Notifications & Push
```
GET  /api/notifications
POST /api/notifications/read/{id}
GET  /api/push/vapid-key
POST /api/push/subscribe
POST /api/push/unsubscribe
```

### Monetization
```
GET  /api/monetization/wallet
POST /api/monetization/tip/{creatorId}
POST /api/payment/webhook             # Razorpay (public)
POST /api/monetization/withdrawal/request
```

### Analytics
```
GET  /api/analytics/overview
GET  /api/analytics/engagement
GET  /api/analytics/followers
```

### Admin
```
GET  /admin/dashboard
GET  /admin/users
POST /admin/users/ban/{id}
POST /admin/users/promote/{id}
GET  /admin/reports
POST /admin/ip-ban
GET  /admin/analytics
GET  /api/admin/export/users
```

---

## 🗄 Database Schema

**Main Tables:**

| Table | Description |
|---|---|
| `users` | User accounts, profile, 2FA, roles |
| `posts` | Videos/posts with metadata |
| `engagements` | Likes, watches, comments, shares |
| `comments` | Nested comments |
| `follows` | Follow relationships |
| `blocks` | Blocked users |
| `bookmarks` | Saved posts |
| `stories` | 24hr stories |
| `story_views` | Story view tracking |
| `direct_messages` | DM messages |
| `live_streams` | Active/ended streams |
| `notifications` | In-app notifications |
| `push_subscriptions` | Web push endpoints |
| `hashtags` | Hashtag registry |
| `post_hashtags` | Post-hashtag mapping |
| `creator_wallets` | Creator earnings |
| `earning_transactions` | Earning history |
| `withdrawal_requests` | Payout requests |
| `email_otp_tokens` | OTP (10 min expiry) |
| `email_verification_tokens` | Email verify links (24hr) |
| `password_reset_tokens` | Reset links (30 min) |
| `reports` | User/post reports |

---

## 🔄 Key Flows

### Registration Flow
```
User fills form
  → POST /api/auth/register
  → Validation (email/username unique check)
  → User save (enabled=false)
  → Method choice: OTP or Link
     → OTP: sendOtp() → email pe 6-digit code
     → Link: sendVerificationEmail() → email pe link
  → User clicks link / enters OTP
  → enabled=true, emailVerified=true
  → Login possible
```

### 2FA Login Flow
```
User enters email + password
  → POST /api/auth/login
  → Password check
  → is2FAEnabled? YES
     → sendOtp(email, "LOGIN_2FA")
     → Frontend shows OTP form
     → POST /api/auth/verify-2fa
     → OTP verify
     → Session set
     → Redirect /feed
  → is2FAEnabled? NO
     → Direct session set
     → Redirect /feed
```

### Video Upload Flow
```
User selects video
  → POST /api/upload/video (multipart)
  → File save → uploads/videos/original/
  → Post record save in DB
  → Kafka: video-processing topic
  → SSE: progress updates to frontend
  → VideoProcessingService (Kafka consumer):
     → FFmpeg: extract audio
     → FFmpeg: generate thumbnail
     → FFmpeg: convert to HLS (.m3u8 + .ts segments)
  → FanoutService: push to followers' Redis feed
  → HashtagService: extract and save hashtags
```

---

## 📱 PWA Support

App phone pe install ho sakti hai:

1. Chrome mein `http://yourapp.com` kholo
2. Address bar mein install icon aayega
3. "Add to Home Screen" click karo

**Service Worker features:**
- Static assets cache (CSS, JS, images)
- Offline fallback page
- Background sync for uploads
- Push notification handling

---

## 🛡️ Admin Panel

Admin panel `/admin/dashboard` pe available hai.

**Roles:**
- `ROLE_ADMIN` — full access
- `ROLE_MODERATOR` — content moderation only
- `ROLE_ANALYST` — read-only analytics

**Admin user kaise banate hain:**
```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your@email.com';
```

---

## 🚀 Deployment

### Production checklist

- [ ] `application-prod.yml` mein sab environment variables set karo
- [ ] `spring.jpa.hibernate.ddl-auto=validate` rakho (update nahi)
- [ ] FFmpeg install karo server pe
- [ ] Redis password set karo
- [ ] Kafka proper topics banao
- [ ] Nginx reverse proxy setup karo
- [ ] SSL certificate lagao (HTTPS)
- [ ] Uploads folder persistent volume pe rakho
- [ ] `application.yml` `.gitignore` mein add karo

### Nginx config example

```nginx
server {
    listen 80;
    server_name yourdomain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws {
        proxy_pass http://localhost:8080/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## 🐛 Known Issues & Fixes Applied

| Bug | Fix |
|---|---|
| `User.enabled = true` default — bina verify login | Changed to `false` |
| Duplicate `@EnableMethodSecurity` annotation | Removed from `AiFeedEngineApplication` |
| `ObjectMapper` LocalDateTime array serialize karta tha | `JavaTimeModule` register kiya |
| Kafka bootstrap servers hardcoded `localhost:9092` | `@Value` se yml se lo |
| OTP tokens delete nahi hote account delete pe | `deleteByEmail()` add kiya |
| 2FA login flow missing | `POST /api/auth/verify-2fa` endpoint add kiya |
| Memory leak — upload status `ConcurrentHashMap` | Redis + TTL se replace kiya |
| `FeedRefillService.removeRange` wrong index | `-201` se `total-201` fix |
| `interests` String set nahi hota `String[]` field pe | `.split(",")` fix |
| Two `JavaMailSender` beans — ambiguity error | `@Primary` Zoho sender pe |
| `RankingService` unused dead code | `@Deprecated` mark kiya |
| Real passwords `application.yml` mein | Environment variables se lo |

---

## 📞 Contact & Support

**Developer:** Yogesh Duhan
**Email:** yogeshduhan7890@gmail.com

---

## 📄 License

This project is for educational and personal use. All rights reserved © 2025 Yogesh Duhan.

---

> 💡 **Tip:** Pehli baar setup mein problem aaye to `application.yml` mein `logging.level.com.yogesh=DEBUG` rakho — sab kuch console mein print hoga.
