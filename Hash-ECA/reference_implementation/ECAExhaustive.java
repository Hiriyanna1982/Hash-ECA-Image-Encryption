package white;

import java.io.PrintWriter;
import java.io.FileWriter;

public class ECAExhaustive {
    public static void main(String[] args) throws Exception {
        String outPath = args.length >= 1 ? args[0] : "eca_outputs.csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(outPath))) {
            pw.println("rule,seed,output");
            for (int rule = 0; rule < 256; rule++) {
                for (int seed = 0; seed < 256; seed++) {
                    int out = DiffusionV2.ecaOneStep(seed, rule);
                    pw.println(rule + "," + seed + "," + out);
                }
            }
        }
        System.out.println("done -> " + outPath);
    }
}
