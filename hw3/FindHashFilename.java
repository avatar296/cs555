import csx55.pastry.util.HashUtil;

public class FindHashFilename {
    public static void main(String[] args) {
        String target = "818e";

        System.out.println("Searching for filename that hashes to " + target + "...");

        // Try simple patterns
        for (int i = 0; i < 100000; i++) {
            String filename = "file" + i + ".txt";
            String hash = HashUtil.hashFilename(filename);

            if (hash.equals(target)) {
                System.out.println("FOUND: " + filename + " -> " + hash);
                return;
            }

            if (i % 1000 == 0 && i > 0) {
                System.out.print("\rTried " + i + " filenames...");
            }
        }

        // Try other patterns
        for (char c1 = 'a'; c1 <= 'z'; c1++) {
            for (char c2 = 'a'; c2 <= 'z'; c2++) {
                for (char c3 = 'a'; c3 <= 'z'; c3++) {
                    String filename = "" + c1 + c2 + c3 + ".txt";
                    String hash = HashUtil.hashFilename(filename);

                    if (hash.equals(target)) {
                        System.out.println("\nFOUND: " + filename + " -> " + hash);
                        return;
                    }
                }
            }
        }

        System.out.println("\nNo match found");
    }
}
