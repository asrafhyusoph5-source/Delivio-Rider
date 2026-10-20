# Delivio Rider (Android)

**Delivio Rider** is the high-converting, production-quality Android courier application for the Delivio Food Delivery platform. Built using **Kotlin**, **Jetpack Compose**, and **Material Design 3**, following MVVM architecture with StateFlow and Navigation Compose.

---

## 🚀 Quick Start & Demo Credentials

To log into the rider app:
- **Email:** `testr@gmail.com`
- **Password:** `Lidesma@1234`
- **Role:** Rider
- **Default Fleet Region:** Puerto Princesa, Palawan, Philippines

---

## 🌟 Key Features

1. **Brand & Theme**
   - Signature Delivio Yellow (`#FFC107`, pressed `#FFB300`, tint `#FFF8E1`) with dark on-yellow text (`#1A1A1A`).
   - Instant App-Wide Light/Dark mode toggle (persisted via Preferences) with day/night map rendering.
   - Default currency symbol: `₱` (Philippine Peso).

2. **Main Interactive Map Screen**
   - Full-screen interactive map with animated heading cone, live/simulated GPS bearing, zoom/pan controls, day/night styles, and Google Maps API key placeholder.
   - Floating circular controls (min 48dp):
     - **Top-Left:** Hamburger Menu (opens 75% drawer panel).
     - **Below Hamburger:** Refresh Nearby Demand button with spin animation.
     - **Top-Center:** Large Online/Offline Pill with pulsing status indicator & confirmation sheet.
     - **Top-Right:** Support Headset button (Live chat, hotline dialer, FAQs).
     - **Bottom-Right:** Recenter / My Location button.
     - **Bottom:** Interactive Order Details bottom sheet (idle wallet card, live radar indicator, or active order card).
   - Hot zones heatmap overlay with dynamic "+₱30 surge bonus" badges.

3. **Complete Order Delivery Flow**
   - Shared platform statuses: `Placed Order` → `Preparing` → `Ready` → `Rider is on the way` → `Delivered` (plus `Cancelled`).
   - **Incoming Order Heads-Up Alert:** 30-second circular countdown timer, merchant details, pickup & drop-off distances, estimated earnings breakdown, payment badge (Cash on Delivery vs Online), and Accept/Decline actions.
   - **Slide-to-Confirm Progression:**
     1. Heading to merchant (turn-by-turn navigation intent)
     2. Arrived at merchant (item checklist verification)
     3. Picked up from merchant
     4. Heading to customer ("Rider is on the way")
     5. Arrived at customer
     6. Delivered with proof of delivery (photo & PIN code) → Celebratory Earnings Success screen.
   - **Call & Message Buttons:** Instant dialer action (`ACTION_DIAL`) and direct in-app chat dialog with quick-reply chips ("I'm outside", "I'm on my way", "Can't find your address").

4. **Test Incoming Order Engine**
   - Quick testing trigger located in Settings & Menu.
   - Scenarios: Restaurant Food Order, Convenience Mart Order, or Cash on Delivery Order.
   - Fully interactive simulation where the rider marker moves along the route on the map canvas.

5. **Rider Drawer & Menu Screens**
   - **Inbox:** Notifications filtered by Orders, Promos, Safety, and System with unread counter.
   - **Schedule:** Shift booking with high-demand slot bonuses (+₱40/hr).
   - **Wallet:** Withdrawable balance, cash on hand vs remittance due, instant cash-out to GCash/Maya/Bank, and full transaction history.
   - **Payments:** Weekly net earnings statements, downloadable CSV summary, and payout account management.
   - **My Profile:** Photo, rating (4.96), acceptance rate (98%), completion rate (99%), vehicle specifications, verified documents, and locked verified legal name (protected against modification).
   - **Service Type (with In-Drawer Toggles):** Expandable menu with on/off switches for Delivio Food, Delivio Instant, Delivio Parcel, and Delivio Pabili.
   - **Resources:**
     - **Tiers:** Complete monthly tier milestones:
       - **Bronze:** 500 rides a month → 200 coins
       - **Silver:** 650 rides a month → 500 coins
       - **Gold:** 850 rides a month → 700 coins
       - **Platinum / Diamond:** 1,000 rides a month → 1,000 coins
     - **Activity Insights:** Weekly earnings charts and best operating hours.
     - **Rider Safety:** SOS emergency dispatch and share live trip.
     - **Tutorials:** Guides on rider operations.
   - **Settings & Privacy:** Theme toggle, navigation preferences, audio & vibration toggles, and secure logout.
