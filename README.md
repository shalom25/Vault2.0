[CENTER][URL='https://bstats.org/plugin/bukkit/vault2/28342'][IMG]https://img.shields.io/bstats/servers/28342?label=Servers&logo=bstats&color=blue[/IMG][/URL] [IMG]https://img.shields.io/github/v/release/shalom25/Vault2.0?display_name=tag[/IMG]
[URL='https://github.com/shalom25/Vault2.0/releases'][IMG]https://img.shields.io/github/downloads/shalom25/Vault2.0/total?label=GitHub&logo=github&color=gray[/IMG][/URL] [URL='https://www.spigotmc.org/resources/129605/'][IMG]https://img.shields.io/spiget/downloads/129605?label=SpigotMC&logo=spigotmc&color=orange[/IMG][/URL] [URL='https://modrinth.com/plugin/vault-2.0-economy-plugins'][IMG]https://img.shields.io/modrinth/dt/vault-2.0-economy-plugins?label=Modrinth&logo=modrinth&color=green[/IMG][/URL]   [URL='https://github.com/shalom25/Vault2.0/wiki'][IMG]https://img.shields.io/badge/Wiki-2563EB?logo=book&logoColor=white[/IMG][/URL]
[IMG]https://i.imgur.com/HxgguHP.png[/IMG][/CENTER]
[LIST]
[*][B][FONT=Tahoma][B]What is Vault2.0?[/B][/FONT][/B]
[*][FONT=Tahoma][B]Vault2.0[/B] is an economy plugin that registers a Bukkit Economy service compatible with the Vault API, allowing other plugins (shops, ranks, etc.) to use money without depending on the original Vault.jar. It includes menus, pay/charge flows, loans, and safe configuration and message reloads[/FONT]
[/LIST]
[B][FONT=Georgia][B][COLOR=#ff0000][B]           ━━━━━━━━━━[/B]IMPORTANT[B]━━━━━━━━━━[/B][/COLOR][/B][/FONT][/B]
[LIST]
[*][FONT=Georgia][COLOR=#660000]Do NOT run this plugin alongside the original Vault.jar (same plugin name). Remove Vault.jar before starting[/COLOR][/FONT]
[/LIST]
[COLOR=#006666][B]          ━━━━━━━━━━[SIZE=5]Features[/SIZE][/B][/COLOR][B][COLOR=#006666]━━━━━━━━━━[/COLOR][/B]
[LIST]
[*][COLOR=#000000]Internal economy with persistence (file storage; optional MySQL).[/COLOR]
[*][COLOR=#000000]/pay with GUI and per-player submenu (pay, charge, view balance, loans).[/COLOR]
[*][COLOR=#000000]Loans with GUI wizard (amounts via chat only).[/COLOR]
[*][COLOR=#000000]Defaulted effects configurable (slowness/fatigue, etc.) when a loan defaults.[/COLOR]
[*][COLOR=#000000]/vault main menu (Pay / Loan / Settings / Reload / Update).[/COLOR]
[*][COLOR=#000000]Safe reload: /vault reload updates config.yml and messages_*.yml without overwriting your values.[/COLOR]
[*][COLOR=rgb(0, 0, 0)]Multi-language: en, es, fr, de, nl, pt, ru, zh_TW, hi.[/COLOR]
[*][COLOR=#000000]GUI History[/COLOR]
[*][COLOR=#000000]Physical Money[/COLOR]
[*][COLOR=#000000]Offline Pay Queue[/COLOR]
[*][COLOR=#000000]Multi-currency support[/COLOR]
[*][COLOR=#000000]Bank + Interest + Tax[/COLOR]
[*][COLOR=#000000]Clan Accounts / Team Vault (SMP and Factions servers)[/COLOR]
[*][COLOR=rgb(0, 0, 0)]Discord webhook - transactions.log anti-duplicate[/COLOR]
[/LIST]
[B][COLOR=#006666]        ━━━━━━━━━━ [SIZE=5]Installation[/SIZE] ━━━━━━━━━━[/COLOR][/B]
[LIST]
[*][COLOR=#000000]Copy the .jar file to the plugins folder on your server. Start the server to generate the configuration.[/COLOR]
[*][COLOR=#000000]MySQL compatibility: compatibility with MySQL, allowing users to integrate and manage databases more efficiently[/COLOR]
[/LIST]
[B][FONT=Georgia][SIZE=5][COLOR=#006666]         ━━━━━━━[/COLOR][/SIZE][/FONT][/B][FONT=Georgia][COLOR=#006666][SIZE=5][B]interactive menu[/B][/SIZE][B][SIZE=5]━━━━━━━━[/SIZE][/B][/COLOR][/FONT]
[LIST]
[*][FONT=Georgia][SIZE=5][SIZE=4]Submenu:[/SIZE][/SIZE][/FONT]
[*][FONT=Georgia][COLOR=#000000]1: pay send money to a player[/COLOR][/FONT]
[*][FONT=Georgia][COLOR=#000000]2: balance shows the player's money[/COLOR][/FONT]
[*][FONT=Georgia][COLOR=rgb(0, 0, 0)]3: Charge sends an interactive message to the player with the designated amount (clicking on the message automatically sends the money without using commands). [/COLOR][/FONT]
[/LIST]
[FONT=Georgia][COLOR=#000000]            [/COLOR]
[COLOR=#006666][B]       [SIZE=5] ━━━━━━━━Loan System━━━━━━━━[/SIZE][/B][/COLOR]
[COLOR=rgb(0, 0, 0)]The loan system helps manage the game's finances. Players can apply for loans, manage payments, and view their financial status.
 [B]Request a Loan[/B]
To request a loan, open the menu with `/loan` or `/prestamo` and select **Request**. Specify the amount and, if there are installments, also the amount of each one.
 [B]Money Delivery[/B]
Upon confirmation, the money is instantly deposited, and the loan is recorded as "active."
 [B]Automatic Collection[/B]
The system attempts to collect installments automatically. If there's enough balance, it deducts from the balance.
[B]View Status[/B]
In the menu, the **Status** option shows the outstanding balance and the next payment date.
 [B]Pay Manually[/B]
You can use the **Pay** option to pay part or all of the loan at any time.
[B] debt[/B]
If there's not enough balance to collect, the loan goes into debt. This can cause negative effects until the debt is settled.
This system simplifies financial management in the game, offering control and dynamism.[/COLOR]
[/FONT]

[FONT=Georgia][COLOR=rgb(0, 0, 0)][spoiler=GILF][/spoiler][/COLOR][spoiler=GILF][/spoiler][/FONT][spoiler=GILF]
[CENTER][IMG]https://i.imgur.com/4eqasJB.gif[/IMG][/CENTER]
[/spoiler]
[B][COLOR=#006666]━━━━━━━━━━ [SIZE=5]Commands[/SIZE] ━━━━━━━━━━[/COLOR][/B]
[spoiler=Click to view all Commads]
/vault              -> open main menu
/vault reload       -> reload config + messages and add missing sections
/vault update       -> check updates
/vault resetbalances (confirm)  -> clear balance
/pay                -> open player list GUI
/loan | /prestamo   -> open loan GUI
/balance            -> show your balance
/eco give/take      -> admin (OP)
[/spoiler]

[B][COLOR=#006666]━━━━━━━━━━ [SIZE=5]Permissions[/SIZE] ━━━━━━━━━━[/COLOR][/B]
[spoiler=Click to view all Permissions]
vault.balance   (default: true)   -> /balance
vault.pay       (default: true)   -> /pay + GUI
vault.loan      (default: true)   -> /loan /prestamo + loans
vault.eco       (default: op)     -> /eco give / take
[/spoiler]
[B][COLOR=rgb(0, 102, 102)]━━━━━━━━━━ [SIZE=5]Placeholders[/SIZE] ━━━━━━━━━━[/COLOR][/B]
[spoiler=Click to view all Placeholders]
- %vault_balance% - Current player's raw balance.
- %vault_balance_formatted% - Current player's balance formatted with the plugin's economy format.
- %vault_eco_balance% - Current player's raw economy balance.
- %vault_eco_balance_formatted% - Current player's formatted economy balance.
- %vault_eco_balance_fixed% - Current player's balance with exactly 2 decimal places.
- %vault_eco_balance_commas% - Current player's balance with comma thousand separators.
- %vault_eco_balance_short% - Current player's abbreviated balance, such as 1.2k or 3.4m .
- %vault_currency_symbol% - Currency symbol from the plugin config.
- %vault_balance_<player>% - Raw balance of the specified player.
- %vault_balance_formatted_<player>% - Formatted balance of the specified player.
- %vault_ecobalance<0-8>dp% - Current player's balance with a custom number of decimal places.
- %vault_top% - Top 10 richest players as a multiline list.
- %vault_top_<n>% - Full top entry for rank n .
- %vault_top_name_<n>% - Player name at rank n .
- %vault_top_amount_<n>% - Formatted balance at rank n .


[/spoiler]

[FONT=Arial][COLOR=#ff0000]Please do not report or post bugs or errors here all reports should be submitted on our Discord server.[/COLOR][/FONT]
[COLOR=#000000][URL='https://discord.gg/SfKvR4CbUj'][IMG]https://i.imgur.com/fT5fdFB.png[/IMG][/URL][/COLOR]
[CENTER][URL='https://bstats.org/plugin/bukkit/vault2/28342'][IMG]https://bstats.org/signatures/bukkit/vault2.svg[/IMG][/URL][/CENTER]
  - `lp group vip permission set vault.pay true`
  - `lp group vip permission set vault.pay.bypass_min true`
  - `lp group vip permission set vault.pay.bypass_max true`
- Load order: `softdepend: [LuckPerms]` ensures LuckPerms is ready during startup.
