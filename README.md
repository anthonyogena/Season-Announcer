# Season Announcer Bot 🤖

A lightweight Java Discord bot built with **JDA 5** that manages and automates recurring 3-week seasonal announcement cycles (1 week active season, 2 weeks off-season). It features dynamic localized Discord timestamps, instant guild slash commands, legacy text command support, and host-agnostic environment configuration.

---

## ✨ Features

* **Automated Cycle Scheduling:** Anchored to a permanent calendar start date to ensure server reboots or crashes never drift the cycle schedule.
* **Dynamic Discord Timestamps:** Displays timestamps using Discord's `<t:UNIX:F>` and live countdown `<t:UNIX:R>` tags, converting times automatically to each user's device timezone.
* **Instant Guild Slash Commands:** Registers commands directly to your server on startup for immediate availability without the 1-hour Discord global command sync delay.
* **Hybrid Command Support:** Handles both native Discord Slash Commands (`/`) and traditional prefix commands (`!`).
* **Panel & Cloud Ready:** Compatible with Wispbyte, Pterodactyl, Docker containers, and local `.env` files via `dotenv-java`.

---

## 🛠️ Commands

| Command | Type | Description |
| :--- | :--- | :--- |
| `/announce` or `!announce` | Admin | Sends the official season start announcement embed with `@everyone` mention. |
| `/endseason` or `!endseason` | Admin | Posts the season conclusion message with a dynamic relative countdown to the next season. |
| `/status` or `!status` | Public | Shows whether a season is currently ACTIVE or in OFF-SEASON along with live countdowns. |
| `/schedule` or `!schedule` | Public | Displays the current cycle timing details. |

---

## 📋 Prerequisites

* **Java JDK:** Version 17 or higher (Java 21 recommended)
* **Build Tool:** Apache Maven 3.8+
* **Discord Bot:** Created on the [Discord Developer Portal](https://discord.com/developers/applications) with `MESSAGE_CONTENT` intent enabled.

---

## ⚙️ Environment Variables

Create a `.env` file in the project root directory (or configure these variables directly in your host's server panel):

```env
# Mandatory
DISCORD_TOKEN=your_discord_bot_token_here
ANNOUNCEMENT_CHANNEL_ID=your_discord_channel_id_here

# Optional (Defaults will be used if omitted)
SEASON_IMAGE_URL=[https://i.imgur.com/<image_name>.jpeg](https://i.imgur.com/<image_name>.jpeg)
TIMEZONE=Asia/Manila
