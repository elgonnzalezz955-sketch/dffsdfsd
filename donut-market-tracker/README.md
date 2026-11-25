# DonutSMP Market Tracker (Fabric, Minecraft 1.21.1)

Client-side stock-market dashboard for DonutSMP. No API key, no external services.
It passively reads the /ah chest GUI you open and records listing prices.

## Get the .jar (no coding tools needed)
1. Create a free GitHub account, make a new empty repository, and upload all files from this folder
   (including the hidden `.github` folder - easiest is drag-and-drop of the whole folder contents).
2. Open the repo's **Actions** tab -> "Build mod jar" -> **Run workflow**.
3. When it finishes (~2 min), open the run and download the **donut-market-tracker-jar** artifact (zip).
   Unzip it: the .jar inside goes into your instance's `mods` folder (Modrinth App -> instance -> Mods -> open folder).
4. You also need Fabric API (add it from the Modrinth App's mod search) and a Fabric 1.21.1 instance.

## Local build (optional)
JDK 21 + Gradle 8.8: `gradle build` -> `build/libs/donut-market-tracker-1.0.0.jar`.

## Use
- Open /ah on DonutSMP and browse/search. Every listing page you view is recorded.
- Press **M** (rebindable) or run `/dmarket` to open the dashboard.
- `/dmarket debug` then open /ah: prints how listings look, to fix the price regex in
  `.minecraft/config/donutmarket/config.json` if nothing is detected.
- `/dmarket status` shows what the scanner is doing.
