package travel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
@Composable fun App(cities: Int, snapshots: Int, mock: Int) {
    MaterialTheme { Text("Travel Agent — offline\nCities: $cities\nOSM candidates: $snapshots\nMock candidates: $mock") }
}
