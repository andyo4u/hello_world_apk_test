# RoadReady Oregon — driving education app for families

RoadReady helps parents teach several teens to drive, following Oregon's
Graduated Driver License (GDL) rules. It lives in this repo next to QuarterHour
as two modules: `drivecore/` (pure Kotlin logic, unit-tested) and `driveapp/`
(the Android app).

> RoadReady is a study and logging aid. It is not affiliated with ODOT/DMV.
> The official source is the *Oregon Driver Manual* and the Oregon Revised
> Statutes (ORS); rules change, so check them before a test.

## Feature brainstorm

✅ = built in this version, 🔜 = planned.

### 1. Several kids, one phone
- ✅ A profile per teen: name, birthday, color, permit date, provisional license date, driver-ed status.
- ✅ Switch between kids from the top bar. Everything (log, quiz progress, skills) is per kid.
- ✅ Each kid's stage is worked out from their dates: *Pre-permit → Instruction permit → Provisional license → Full privileges*.
- 🔜 Parent PIN so a teen can't edit their own log or sign off skills.
- 🔜 Sync between two parents' phones (shared family file or cloud backup).
- 🔜 Sibling leaderboard (quiz streaks, hours this month) — opt-in.

### 2. Supervised driving log (Oregon GDL)
- ✅ Start/stop timer that survives the app being closed, or manual entry.
- ✅ Day vs **night** minutes worked out automatically from sunset/sunrise at your home city (NOAA solar formula). Oregon doesn't require night hours, but they're good practice and tracked separately.
- ✅ Tag each drive: city, residential, highway/freeway, rural, rain, night, snow/ice, fog, gravel, parking, mountain pass.
- ✅ Supervisor name, notes, delete/edit.
- ✅ Progress toward **100 hours**, or **50 hours** when the teen takes an ODOT-approved driver-ed course.
- ✅ Share the log as CSV plus a plain-text summary to fill in the DMV's driving experience certification.
- 🔜 GPS trip recording with route map and auto-tagging (highway vs city by speed).
- 🔜 Post-drive debrief: "What went well / what to practice", linked to skills.
- 🔜 Weather auto-tagging.

### 3. GDL milestone and restriction tracker
- ✅ Dates the teen can apply for a permit (15) and a provisional license (16 *and* 6 months after the permit).
- ✅ Checklist: age, permit time, hours, driver ed, drive test.
- ✅ Live restrictions for a provisional driver, with the date each one ends:
  - first 6 months: no passengers under 20 who aren't immediate family;
  - months 6–12: at most 3 such passengers;
  - first year: no driving midnight–5 a.m. (exceptions: home↔work, work, home↔school event with no other ride, or with a licensed driver 25+);
  - passenger and night limits end at 1 year or age 18, whichever comes first;
  - under 18: no phone use at all, hands-free included.
- ✅ Permit reminders: supervising driver 21+ with a license, in the front passenger seat.
- 🔜 Notifications: "Maya can apply for her provisional license in 2 weeks", "Restriction ends today".

### 4. Oregon rules of the road
- ✅ Topic lessons with key facts and the ORS section: GDL, speed limits, right of way and crosswalks, signals and turns, signs and markings, parking, sharing the road, school buses and emergency vehicles, alcohol and phones, night and weather, emergencies and crashes.
- 🔜 Sign gallery with drawings of every common sign.
- 🔜 Spanish translation (the DMV offers the test in Spanish).
- 🔜 Audio mode for listening in the car (as a passenger!).

### 5. Practice tests
- ✅ Question bank tagged by topic, each with an explanation.
- ✅ **Mock knowledge test**: 35 questions, pass with 28 (80%), like the DMV test.
- ✅ Quick practice (10 questions) that favors the kid's weak questions (Leitner boxes).
- ✅ Practice one topic.
- ✅ History and a "ready for the test" score per kid.
- 🔜 Daily streaks and reminders.

### 6. Behind-the-wheel skills curriculum
- ✅ A guided checklist from parking lot to freeway: cockpit drill, turns, 4-way stops, lane changes, parallel parking, freeway merging, roundabouts, railroad crossings, rain, night, chains, mountain passes and more.
- ✅ Each skill has a coaching tip; parent rates it *Not started → Introduced → Practicing → Confident*.
- 🔜 Suggested next lesson based on hours and skills.
- 🔜 Drive-test rehearsal mode (the examiner's route checklist).

### 7. Ideas for later
- Car profile (mirrors/seat settings per kid, maintenance basics: tire pressure, oil, wipers).
- "What to do after a crash" card with an exchange-info form and photos.
- Insurance discount helper (good-student, driver-ed certificate).
- Parent-teen driving contract with sign-off.
- Android Auto / do-not-disturb while driving.

## Privacy

Everything stays on the phone (no account, no network permission). Backups
are off so a kid's log isn't silently copied elsewhere; the CSV share is the
way to get data out.

## Oregon facts used (and sources)

- Permit at 15; knowledge test 35 questions, 28 to pass; permit held 6 months; provisional license at 16 with 100 supervised hours, or 50 plus an ODOT-approved course — ODOT DMV teen licensing page.
- Provisional passenger/night/phone limits — ODOT DMV teen licensing page, ORS 807.122.
- Speed: 15 alley, 20 business district and school zone, 25 residential and ocean shore — ORS 811.111, Oregon Driver Manual.
- Headlights sunset–sunrise and when visibility < 1,000 ft; dim within 500 ft oncoming, 350 ft following — ORS 811.515/811.520.
- No parking within 10 ft of a hydrant, 20 ft of a crosswalk at an intersection, 50 ft of a railroad crossing — ORS 811.550.
- Every intersection is a crosswalk; stay stopped until the pedestrian clears your lane and the next; turning at a signal, the lane you turn into plus 6 ft — ORS 811.028.
- Left on red onto a one-way street in the direction of traffic — ORS 811.360.
- School bus red lights: stop both directions except across a divided highway with an unpaved median or barrier — ORS 811.155.
- Move over or slow 5 mph below the limit for emergency and any stopped vehicle with hazards — ORS 811.147.
- Bicycle passing above 35 mph: room enough that a fallen rider wouldn't be hit — ORS 811.065.
- Under 21: any alcohol is a DUII — ORS 813.010/813.300.
