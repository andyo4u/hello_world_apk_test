package app.roadready.core

data class Skill(val id: String, val title: String, val tip: String)

data class SkillGroup(val title: String, val skills: List<Skill>)

/** Behind-the-wheel curriculum, roughly in the order a parent would teach it. Ids are stable. */
object Skills {
    val groups: List<SkillGroup> = listOf(
        SkillGroup(
            "1 · Parking lot basics",
            listOf(
                Skill("cockpit", "Cockpit drill", "Seat, mirrors, belt, and know every control before the engine starts."),
                Skill("mirrors", "Mirror setup and blind spots", "Set side mirrors so the car's side just leaves view; still shoulder-check."),
                Skill("startstop", "Smooth starts and stops", "Squeeze the brake; aim to stop without a lurch."),
                Skill("steering", "Hand position and steering", "Hands at 9 and 3; push-pull steering in turns."),
                Skill("backing", "Backing straight", "Look over your right shoulder, go slowly, small corrections."),
            ),
        ),
        SkillGroup(
            "2 · Neighborhood streets",
            listOf(
                Skill("rightturns", "Right turns", "Signal 100 ft early, slow before the turn, turn into the nearest lane."),
                Skill("leftturns", "Left turns", "Yield to oncoming cars and pedestrians; keep wheels straight while waiting."),
                Skill("stopsigns", "Stop signs and 4-way stops", "Full stop behind the line; first to stop goes first, tie goes to the right."),
                Skill("crosswalks", "Crosswalks and pedestrians", "Every corner is a crosswalk. Wait until they clear your lane and the next."),
                Skill("scanning", "Scanning 12 seconds ahead", "Look far ahead, check mirrors every 5–8 seconds."),
            ),
        ),
        SkillGroup(
            "3 · City driving",
            listOf(
                Skill("lanechange", "Lane changes", "Signal, mirror, shoulder check, then move smoothly."),
                Skill("signals", "Traffic signals and arrows", "Know flashing yellow arrows and when left on red is legal."),
                Skill("bikes", "Bike lanes and cyclists", "Check the bike lane before every right turn."),
                Skill("parallel", "Parallel parking", "End within 12 inches of the curb; practice between cones first."),
                Skill("angle", "Angle and perpendicular parking", "Go slowly, use reference points, back out with care."),
                Skill("hillpark", "Parking on hills", "Downhill toward the curb, uphill away, no curb toward the edge."),
                Skill("threepoint", "Three-point turn", "A common drive-test item: check traffic before every move."),
            ),
        ),
        SkillGroup(
            "4 · Highways",
            listOf(
                Skill("merge", "Merging onto a freeway", "Match speed on the ramp and find your gap early."),
                Skill("exit", "Exiting", "Signal early, move into the exit lane, slow down on the ramp, not the freeway."),
                Skill("following", "Following distance", "2 seconds minimum; count from a fixed marker."),
                Skill("passing", "Passing on a two-lane road", "Only where allowed and when you can see far enough to finish safely."),
                Skill("moveover", "Move over for stopped vehicles", "Change lanes or slow 5 mph under the limit."),
            ),
        ),
        SkillGroup(
            "5 · Conditions",
            listOf(
                Skill("night", "Night driving", "Dim within 500 ft oncoming, 350 ft following."),
                Skill("rain", "Rain", "Headlights on, double your following distance, avoid hard braking."),
                Skill("fog", "Fog", "Low beams, slow down, follow the right edge line."),
                Skill("snow", "Snow and ice", "Gentle everything; practice in an empty lot first."),
                Skill("chains", "Putting on chains", "Practice at home before you need them on a pass."),
                Skill("gravel", "Gravel and rural roads", "Slow down, watch for wildlife and farm equipment."),
                Skill("mountain", "Mountain passes", "Use lower gears downhill; watch for chain requirements."),
            ),
        ),
        SkillGroup(
            "6 · Special situations",
            listOf(
                Skill("roundabout", "Roundabouts", "Yield on entry, go counterclockwise, signal your exit."),
                Skill("railroad", "Railroad crossings", "Stop 15–50 ft back when required; never stop on the tracks."),
                Skill("schoolbus", "School buses and school zones", "20 mph in school zones; stop both ways for red lights."),
                Skill("emergencyveh", "Emergency vehicles", "Pull right and stop until they pass."),
                Skill("fuel", "Fueling up", "Engine off, no phone, stay with the nozzle."),
                Skill("breakdown", "Breakdowns and crashes", "Hazards on, get off the road, know what to exchange."),
            ),
        ),
    )

    val all: List<Skill> = groups.flatMap { it.skills }

    /** 0–100: share of the curriculum rated Confident, with partial credit for earlier levels. */
    fun progress(levels: Map<String, SkillLevel>): Int {
        val score = all.sumOf { (levels[it.id] ?: SkillLevel.NOT_STARTED).ordinal }
        return score * 100 / (all.size * SkillLevel.CONFIDENT.ordinal)
    }
}
