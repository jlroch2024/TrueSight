import com.truesight.relationship.ReportSection;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes what the app would send the AI for one report, so that measuring uses the app's own code. Used by
 * measure_report_reading.py: java -cp backend/target/classes docs/examples/SendSection.java 10-K in.txt out.txt
 */
public class SendSection {

    public static void main(String[] args) throws Exception {
        Files.writeString(Path.of(args[2]), ReportSection.forAi(args[0], Files.readString(Path.of(args[1]))));
    }
}
