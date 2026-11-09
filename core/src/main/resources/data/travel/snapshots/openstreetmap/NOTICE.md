# OpenStreetMap data notice

The snapshots in this directory contain data derived from OpenStreetMap.

© OpenStreetMap contributors. OpenStreetMap data is available under the Open Database License
(ODbL): https://www.openstreetmap.org/copyright

The Boston attractions snapshot was acquired through the public Overpass API on
2026-09-16 at 05:36:07 UTC. The one-time query selected named visitor POIs inside the Boston,
Massachusetts administrative area. The project normalized observed names, address components,
OSM element identities, tags, and returned coordinates into its local candidate contract.

The Boston hotels snapshot was acquired through the same endpoint on 2026-09-16 at 06:26:35 UTC
(OSM base timestamp 06:23:21 UTC). Its area query returned named `tourism=hotel` objects only.
The project retained three conventional hotels with complete observed addresses, valid returned
coordinates, and no duplicate nearby hotel representation. It did not import hotel stars, ratings,
reviews, booking prices, or live availability.

The Boston restaurants snapshot was acquired through the same endpoint on 2026-09-16 at
12:07:21 UTC (OSM base timestamp 12:04:06 UTC). Its area query returned 868 named
`amenity=restaurant` objects. The project retained three distinct restaurants with complete
observed addresses, valid coordinates, and useful observed cuisine or diet tags. Cuisine and diet
values remain OpenStreetMap metadata; deterministic preference tags are kept separately as planner
metadata. No rating, review count, image, menu price, or live availability was imported.

The Austin snapshots were acquired through the same endpoint on 2026-09-16 using the Texas →
Austin administrative-area strategy. Attractions were acquired at 14:53:48 UTC (OSM base
14:51:00 UTC; 910 returned objects), hotels at 14:54:27 UTC (OSM base 14:52:01 UTC; 213 returned
objects), and restaurants at 14:55:27 UTC (OSM base 14:53:01 UTC; 1,101 returned objects). The
project retained three distinct objects in each category with stable OSM identities, valid
coordinates, and useful observed address/category metadata. It did not import admission prices,
room rates, menu prices, ratings, reviews, images, reservations, or live availability.

The Columbus snapshots were acquired through the same endpoint on 2026-09-16 using the Ohio →
Columbus administrative-area strategy. Attractions were acquired at 15:20:11 UTC (OSM base
15:17:18 UTC; 482 returned objects), hotels finalized at 15:22:28 UTC (OSM base 15:18:20
UTC; 140 returned objects), and restaurants at 15:22:39 UTC (OSM base 15:19:21 UTC; 797
returned objects). Five attractions, four hotels, and six restaurants were retained with stable
OSM identities, observed addresses, valid coordinates, and observed category metadata. The
administrative-area attraction response contained no `tourism=zoo` object, so no zoo provider fact
was invented. Restaurant cuisine values are normalized only from observed OSM tags; legacy
`fried chicken` preference tags remain separate planner metadata. No admission price, room rate,
menu price, rating, review, image, reservation, or live availability was imported.

Phase O5 acquired the remaining supported cities in three controlled waves on 2026-09-16: New
York City, Chicago, Washington DC; Miami, Denver, Seattle; and San Francisco, Los Angeles, Las
Vegas. Every request used a verified OSM administrative relation. Denver hotel/food, Los Angeles,
and Las Vegas hotel/food queries additionally used the exact bounding box returned by that relation
plus an exact `addr:city` filter to avoid overloaded area indexes and exclude neighboring cities.
The repository retains only normalized snapshots and selected-source provenance; broad raw
responses remain in the system temporary acquisition cache. Each selected candidate has an OSM
element identity, valid coordinates, a usable observed address, and provider category metadata.
One selected San Francisco restaurant lacks an observed cuisine tag; no cuisine was inferred from
its name. No rating, review count, image, admission price, room rate, menu price, reservation, or
live availability was imported.

Candidate prices are project-generated planner estimates preserved for deterministic demo behavior.
They are not OpenStreetMap data, admission prices, room rates, meal prices, or current ticket prices.
