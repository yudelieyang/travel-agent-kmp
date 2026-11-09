package travel

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import travel_agent_kmp.composeapp.generated.resources.*

@Composable fun categoryLabel(key: String, singular: Boolean = false): String = when (key) {
    "attractions" -> stringResource(if (singular) Res.string.sight else Res.string.sights)
    "hotel" -> stringResource(Res.string.hotel)
    "food" -> stringResource(Res.string.food)
    "transport" -> stringResource(Res.string.transit)
    "breakfast" -> stringResource(Res.string.breakfast)
    "lunch" -> stringResource(Res.string.lunch)
    "dinner" -> stringResource(Res.string.dinner)
    else -> key
}
