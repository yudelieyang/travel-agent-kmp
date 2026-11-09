package travel.android
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import travel.App
import travel.SnapshotLoader
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = SnapshotLoader()
        setContent { App(data.cityCount(), data.snapshotCandidateCount(), data.mockCandidateCount()) }
    }
}
