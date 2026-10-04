package app.roadready.core

data class Fact(val text: String, val source: String = "")

data class Topic(val id: String, val title: String, val summary: String, val facts: List<Fact>)

/**
 * Oregon rules of the road, condensed from the Oregon Driver Manual and the
 * Oregon Revised Statutes. A study aid: the official manual is the source of truth.
 */
object RulesOfTheRoad {
    const val DISCLAIMER =
        "Study aid only, not legal advice and not affiliated with ODOT. Check the official Oregon Driver Manual " +
            "(oregon.gov/odot/dmv) before your test; laws change."

    val topics: List<Topic> = listOf(
        Topic(
            "gdl", "Licensing for teens",
            "Oregon's graduated license: permit at 15, provisional at 16, limits until 18.",
            listOf(
                Fact("You can get an instruction permit at 15 after passing a vision screening and the knowledge test.", "ORS 807.280"),
                Fact("The knowledge test has 35 questions. You need 28 right (80%) to pass."),
                Fact("With a permit, a licensed driver 21 or older must sit in the front passenger seat.", "ORS 807.280"),
                Fact("You must hold the permit 6 months and be 16 before a provisional license.", "ORS 807.065"),
                Fact("Log 100 hours of supervised driving, or 50 hours plus an ODOT-approved traffic safety education course. The supervisor must be 21+ and licensed 3+ years.", "ORS 807.065"),
                Fact("First 6 months of a provisional license: no passengers under 20 who aren't immediate family.", "ORS 807.122"),
                Fact("Months 6–12: no more than 3 passengers under 20 who aren't immediate family.", "ORS 807.122"),
                Fact("First year: no driving from midnight to 5 a.m., except home↔work, for work, home↔school event with no other ride, or with a licensed driver 25+.", "ORS 807.122"),
                Fact("Passenger and night limits end after 1 year or at 18, whichever comes first."),
                Fact("Drivers under 18 can't use a phone at all while driving, not even hands-free.", "ORS 811.507"),
            ),
        ),
        Topic(
            "speed", "Speed limits",
            "The basic rule plus Oregon's statutory speeds.",
            listOf(
                Fact("Basic speed rule: never drive faster than is reasonable and prudent for traffic, the road, the weather and visibility — even under the posted limit.", "ORS 811.100"),
                Fact("15 mph in alleys.", "ORS 811.111"),
                Fact("20 mph in business districts.", "ORS 811.111"),
                Fact("20 mph in school zones when the sign's conditions apply: lights flashing, or children present (in or waiting at the crosswalk, or a crossing guard is there).", "ORS 811.106"),
                Fact("25 mph in residential districts and on ocean shores where driving is allowed.", "ORS 811.111"),
                Fact("55 mph on most highways unless a different limit is posted; some interstates are posted higher.", "ORS 811.111"),
                Fact("Driving too slowly and blocking traffic is also illegal; slower traffic keeps right.", "ORS 811.130"),
            ),
        ),
        Topic(
            "rightofway", "Right of way and pedestrians",
            "Who goes first, and Oregon's strict crosswalk law.",
            listOf(
                Fact("Every intersection is a crosswalk, marked or not."),
                Fact("Stop for a pedestrian who is crossing or stepping into the crosswalk. Stay stopped until they've cleared your lane and the lane next to it.", "ORS 811.028"),
                Fact("Turning at a traffic signal: stay stopped until the pedestrian is past the lane you're turning into and 6 feet into the next one.", "ORS 811.028"),
                Fact("Never pass a car that's stopped at a crosswalk; someone may be crossing in front of it.", "ORS 811.020"),
                Fact("Yield to a blind pedestrian with a white cane or guide dog, always.", "ORS 811.035"),
                Fact("At an uncontrolled intersection, yield to the vehicle on your right if you arrive at the same time."),
                Fact("At a 4-way stop, the first to stop goes first; if two stop together, the one on the right goes first."),
                Fact("Turning left, yield to oncoming traffic and pedestrians."),
                Fact("At a roundabout, yield to traffic already in it, go counterclockwise, and signal when you exit."),
            ),
        ),
        Topic(
            "signals", "Signals and turns",
            "Lights, arrows and turning on red.",
            listOf(
                Fact("Signal continuously for at least the last 100 feet before turning or changing lanes.", "ORS 811.335"),
                Fact("Right on red is allowed after a full stop and yielding, unless a sign prohibits it.", "ORS 811.360"),
                Fact("Left on red is allowed only onto a one-way street going in the direction of traffic, after a full stop and yielding.", "ORS 811.360"),
                Fact("Flashing red light: treat it like a stop sign."),
                Fact("Flashing yellow light: slow down and proceed with caution."),
                Fact("Flashing yellow arrow: you may turn left after yielding to oncoming traffic and pedestrians."),
                Fact("A traffic signal that's dark (power out) is treated as an all-way stop."),
                Fact("Turn from the closest lane into the closest lane unless signs or markings say otherwise."),
            ),
        ),
        Topic(
            "signs", "Signs and markings",
            "Read a sign by its shape and color; read the road by its lines.",
            listOf(
                Fact("Octagon (8 sides) = stop. Triangle pointing down = yield."),
                Fact("Pentagon (5 sides) = school zone or school crossing."),
                Fact("Round yellow sign = railroad crossing ahead. The white X-shaped crossbuck marks the crossing itself."),
                Fact("Diamond = warning (curves, merges, hazards). Rectangle = regulatory or guide."),
                Fact("Orange = construction or work zone. Fines are higher when workers are present.", "ORS 811.230"),
                Fact("Yellow lines separate traffic going opposite ways; white lines separate traffic going the same way."),
                Fact("A solid yellow line on your side means no passing."),
                Fact("A center lane with solid and dashed yellow lines on both sides is a two-way left-turn lane — only for turning, not travel or passing."),
            ),
        ),
        Topic(
            "parking", "Parking",
            "Where not to park, and parking on hills.",
            listOf(
                Fact("No parking within 10 feet of a fire hydrant.", "ORS 811.550"),
                Fact("No parking within 20 feet of a crosswalk at an intersection.", "ORS 811.550"),
                Fact("No parking within 50 feet of a railroad crossing.", "ORS 811.550"),
                Fact("No parking in a crosswalk, on a sidewalk, in an intersection, or blocking a driveway.", "ORS 811.550"),
                Fact("Downhill with a curb: turn wheels toward the curb. Uphill with a curb: turn wheels away from the curb. No curb: turn wheels toward the edge of the road."),
                Fact("Parallel park with your wheels within 12 inches of the curb.", "ORS 811.570"),
                Fact("Disabled parking needs a placard or plate; fines are steep.", "ORS 811.615"),
            ),
        ),
        Topic(
            "sharing", "Sharing the road",
            "Bicycles, motorcycles, trucks and trains.",
            listOf(
                Fact("Above 35 mph, pass a bicycle with enough room that the rider wouldn't be hit if they fell into your lane.", "ORS 811.065"),
                Fact("A bike lane is a lane: yield to bicyclists in it before you turn across it.", "ORS 811.050"),
                Fact("Give motorcycles a full lane width; don't share a lane with one."),
                Fact("Trucks have big blind spots: if you can't see the driver's mirrors, they can't see you."),
                Fact("At a railroad crossing with a signal or a train coming, stop between 15 and 50 feet from the nearest rail.", "ORS 811.455"),
                Fact("Never stop on the tracks. Only start across when there's room for your whole car on the other side."),
            ),
        ),
        Topic(
            "emergency", "School buses and emergency vehicles",
            "When you must stop, move over, or pull over.",
            listOf(
                Fact("When a school bus flashes red lights, stop — in both directions on any road, including multi-lane roads.", "ORS 811.155"),
                Fact("The only exception: on a divided highway with an unpaved median or barrier, traffic on the other side doesn't stop.", "ORS 811.155"),
                Fact("Stay stopped until the red lights turn off."),
                Fact("Emergency vehicle with lights or siren coming: pull to the right edge and stop until it passes.", "ORS 811.145"),
                Fact("Move over: for an emergency vehicle, tow truck, road crew, or any stopped car showing hazard lights, move out of the next lane if safe; if not, slow to at least 5 mph under the speed limit.", "ORS 811.147"),
            ),
        ),
        Topic(
            "impaired", "Alcohol, drugs and distraction",
            "Oregon DUII and phone laws.",
            listOf(
                Fact("Adults: a blood alcohol content of 0.08% or more is DUII.", "ORS 813.010"),
                Fact("Under 21: any amount of alcohol in your body while driving is against the law.", "ORS 813.300"),
                Fact("Cannabis and many prescription or over-the-counter drugs can cause DUII too."),
                Fact("Implied consent: by driving, you've agreed to a breath test if arrested for DUII. Refusing suspends your license.", "ORS 813.100"),
                Fact("Holding or using a phone while driving is illegal at any age; under 18, hands-free is banned too.", "ORS 811.507"),
                Fact("Open alcohol containers aren't allowed in the passenger area.", "ORS 811.170"),
            ),
        ),
        Topic(
            "conditions", "Night, weather and following distance",
            "Seeing, being seen, and space around your car.",
            listOf(
                Fact("Headlights on from sunset to sunrise, and whenever you can't see 1,000 feet ahead.", "ORS 811.515"),
                Fact("Dim high beams within 500 feet of an oncoming car, and within 350 feet when following one.", "ORS 811.520"),
                Fact("Keep at least a 2-second following distance in good conditions; 4 or more in rain, at night, or behind trucks."),
                Fact("Hydroplaning: ease off the gas, don't brake hard, and steer straight until the tires grip again."),
                Fact("Skidding: look and steer where you want the car to go."),
                Fact("Fog: use low beams and slow down. High beams reflect off fog."),
                Fact("Watch for signs requiring chains or traction tires on mountain passes; carry chains in winter."),
                Fact("Bridges and shaded spots freeze first."),
            ),
        ),
        Topic(
            "crashes", "Crashes and emergencies",
            "What the law expects after a crash.",
            listOf(
                Fact("Stop at the scene. Leaving it is a crime.", "ORS 811.700"),
                Fact("Swap names, addresses, license and insurance information with the other driver.", "ORS 811.700"),
                Fact("Report a crash to DMV within 72 hours if anyone is hurt or killed, your car has over $2,500 damage, any car has over $2,500 damage and is towed, or other property has over $2,500 damage. File even if police made a report.", "ORS 811.720"),
                Fact("Hitting an unattended car: find the owner or leave a note with your contact information.", "ORS 811.700"),
                Fact("You must carry proof of insurance in the car.", "ORS 806.012"),
                Fact("Brakes fail: pump the brake pedal, shift to a lower gear, and use the parking brake gently."),
                Fact("Tire blowout: grip the wheel, ease off the gas, and don't brake hard until the car is under control."),
            ),
        ),
        Topic(
            "passengers", "Seat belts and child seats",
            "Everyone buckles up.",
            listOf(
                Fact("Everyone in the car must wear a seat belt or be in a proper child seat.", "ORS 811.210"),
                Fact("Children under 2 ride in a rear-facing car seat.", "ORS 811.210"),
                Fact("Kids over 40 pounds ride in a booster (or harness seat) until they're 4'9\" tall or 8 years old, and the adult belt fits.", "ORS 811.210"),
                Fact("The driver is responsible for passengers under 16 being buckled up.", "ORS 811.215"),
            ),
        ),
    )

    fun topic(id: String): Topic? = topics.firstOrNull { it.id == id }
}
