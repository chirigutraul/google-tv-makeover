# 6. Twitch "Live now" card (optional)

When you select Twitch in the dock, the home screen checks your favourite streamers and shows a card for each one who's live: stream preview, avatar, title, game and viewers. ↑ selects a card, OK opens the stream in the Twitch app, ← → scroll when several are live. The line under "Twitch" says "1 of your 3 streamers is live".

It uses Twitch's official API with an app token: no Twitch sign-in, and it only checks when Twitch is selected (at most once a minute).

## 1. Get free keys (about 5 minutes)

1. Sign in at [dev.twitch.tv/console](https://dev.twitch.tv/console). Twitch requires **two-factor authentication** on your account first.
2. **Register Your Application**:
   - Name: anything unique
   - OAuth Redirect URLs: `http://localhost`
   - Category: Application Integration
   - Client Type: Confidential
3. **Manage** → copy the **Client ID** → **New Secret** → copy the **Client Secret** (shown once).

## 2. Add them to local.properties

Edit the file yourself (don't paste the secret into a chat):

```properties
twitch.clientId=YOUR_CLIENT_ID
twitch.clientSecret=YOUR_CLIENT_SECRET
twitch.streamers=streamer1,streamer2
```

Usernames as in `twitch.tv/<username>`. Then rebuild and reinstall ([step 3](03-home-screen.md)).

## Check the keys work (optional)

```bash
ID=$(grep '^twitch.clientId=' local.properties | cut -d= -f2)
SECRET=$(grep '^twitch.clientSecret=' local.properties | cut -d= -f2)
TOKEN=$(curl -s -X POST https://id.twitch.tv/oauth2/token -d "client_id=$ID&client_secret=$SECRET&grant_type=client_credentials" | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
curl -s -H "Client-Id: $ID" -H "Authorization: Bearer $TOKEN" "https://api.twitch.tv/helix/streams?user_login=streamer1&user_login=streamer2"
```

## Security

The keys are compiled into the APK on your TV. Don't share the APK. A leaked key only allows Twitch lookups under your app's name (no access to your account); if it leaks, click **New Secret** on dev.twitch.tv and rebuild.
