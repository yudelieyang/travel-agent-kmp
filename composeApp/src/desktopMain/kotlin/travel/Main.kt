package travel
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
fun main() {
    val data = SnapshotLoader()
    val cities = data.cityCount()
    val snapshots = data.snapshotCandidateCount()
    val mock = data.mockCandidateCount()
    println("Cities: $cities; OSM candidates: $snapshots; Mock candidates: $mock")
    application { Window(onCloseRequest = ::exitApplication, title = "Travel Agent") { App(cities, snapshots, mock) } }
}
