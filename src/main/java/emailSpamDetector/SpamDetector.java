package emailSpamDetector;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public class SpamDetector {
    // Maps to hold word frequencies for ham and spam folders
    private final Map<String, Integer> trainHamFreq;
    private final Map<String, Integer> trainSpamFreq;
    // Map to hold calculated spam probabilities for each word
    private final Map<String, Double> wordSpamProbabilities;
    private int numHamFiles;
    private int numSpamFiles;
    // Set of common words to ignore
    private final Set<String> stopWords = new HashSet<>(Arrays.asList(
        "the", "is", "in", "and", "to", "of", "a", "that", "it", "on", "for", "you", 
        "this", "with", "but", "or", "not", "are", "from", "by", "as", "at", "was"
    ));
    // Constructor to initialize spam detector and train it with the given dataset
    public SpamDetector(File mainDirectory) {
        trainHamFreq = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        trainSpamFreq = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        wordSpamProbabilities = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        numHamFiles = 0;
        numSpamFiles = 0;

        System.out.println("\n=== INITIALIZING SPAM DETECTOR ===");
        if (mainDirectory == null || !mainDirectory.exists()) {
            System.err.println("ERROR: Invalid directory selected!");
            return;
        }

        System.out.println("Selected directory: " + mainDirectory.getAbsolutePath());
        train(mainDirectory);
        calculateProbabilities();
    }

    // Trains the spam detector using training dataset
    private void train(File mainDirectory) {
        File trainDir = new File(mainDirectory, "train");
        System.out.println("\n=== TRAINING PHASE ===");
        System.out.println("Training directory: " + trainDir.getAbsolutePath());

        if (!trainDir.exists()) {
            System.err.println("FATAL ERROR: 'train' folder not found!");
            return;
        }

        // Process ham folders
        processHamFolder(trainDir, "ham");
        processHamFolder(trainDir, "ham2"); // Handles case-insensitive names

        // Process spam
        processSpamFolder(trainDir);

        System.out.println("\nTraining Summary:");
        System.out.println("- Total ham files: " + numHamFiles);
        System.out.println("- Total spam files: " + numSpamFiles);
    }

    // Process the ham folders
    private void processHamFolder(File trainDir, String folderName) {
        File hamDir = new File(trainDir, folderName);
        if (!hamDir.exists()) {
            System.err.println("WARNING: Folder '" + folderName + "' not found in train directory");
            return;
        }

        System.out.println("\nProcessing " + folderName + "...");
        int count = processDirectory(hamDir, trainHamFreq);
        numHamFiles += count;
        System.out.println("-> Processed " + count + " files from " + folderName);
    }

    // Process the spam emails
    private void processSpamFolder(File trainDir) {
        File spamDir = new File(trainDir, "spam");
        if (!spamDir.exists()) {
            System.err.println("FATAL ERROR: 'spam' folder not found!");
            return;
        }

        System.out.println("\nProcessing spam...");
        numSpamFiles = processDirectory(spamDir, trainSpamFreq);
        System.out.println("-> Processed " + numSpamFiles + " spam files");
    }

    // Process a directory of files and updates the frequency map
    private int processDirectory(File directory, Map<String, Integer> freqMap) {
        int fileCount = 0;
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null && files.length > 0) {
                System.out.println("Found " + files.length + " files in " + directory.getName());
                for (File file : files) {
                    parseFile(file).forEach(word -> {
                        if (!stopWords.contains(word)) {
                            freqMap.put(word, freqMap.getOrDefault(word, 0) + 1);
                        }
                    });
                }
                fileCount = files.length;
            } else {
                System.err.println("WARNING: Empty directory - " + directory.getAbsolutePath());
            }
        } else {
            System.err.println("ERROR: Invalid directory - " + directory.getAbsolutePath());
        }
        return fileCount;
    }

    // Parse a file and extracts unique words, ignoring non-alphanumeric characters
    private Set<String> parseFile(File file) {
        Set<String> words = new HashSet<>();
        try (Scanner scanner = new Scanner(file)) {
            Pattern pattern = Pattern.compile("[^a-zA-Z]");
            while (scanner.hasNext()) {
                String word = pattern.matcher(scanner.next().toLowerCase()).replaceAll("");
                if (!word.isEmpty()) words.add(word);
            }
        } catch (FileNotFoundException e) {
            System.err.println("ERROR: File not found - " + file.getAbsolutePath());
        }
        return words;
    }

    // Calculate the probability of an email being spam based on training dataset
    private void calculateProbabilities() {
        System.out.println("\n=== CALCULATING PROBABILITIES ===");
        double totalHam = numHamFiles + 1.0; // Laplace smoothing to avoid zero probabilities
        double totalSpam = numSpamFiles + 1.0;

        // Collects all unique words from ham and spam training sets
        Set<String> allWords = new HashSet<>();
        allWords.addAll(trainHamFreq.keySet());
        allWords.addAll(trainSpamFreq.keySet());
        System.out.println("Total unique words: " + allWords.size());

        // Calculate the probability of a word being in spam versus ham
        for (String word : allWords) {
            double prWiH = (trainHamFreq.getOrDefault(word, 0) + 1.0) / totalHam;
            double prWiS = (trainSpamFreq.getOrDefault(word, 0) + 1.0) / totalSpam;
            double prSWi = prWiS / (prWiS + prWiH);
            wordSpamProbabilities.put(word, prSWi);
        }
        System.out.println("Probability calculation complete!");
    }

    // Tests files in the main directory
    public List<TestFile> test(File mainDirectory) {
        System.out.println("\n=== TESTING PHASE ===");
        List<TestFile> results = new ArrayList<>();
        File testDir = new File(mainDirectory, "test");

        // Ensure the test directory exists before continuing
        if (!testDir.exists()) {
            System.err.println("FATAL ERROR: 'test' folder not found!");
            return results;
        }

        // Process ham and spam folders
        processTestFolder(new File(testDir, "ham"), "Ham", results);
        processTestFolder(new File(testDir, "spam"), "Spam", results);
        System.out.println("Total test files processed: " + results.size());
        return results;
    }

    // Process the given test folder and classify its files
    private void processTestFolder(File folder, String actualClass, List<TestFile> results) {
        System.out.println("\nProcessing test folder: " + folder.getName());
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null && files.length > 0) {
                System.out.println("Found " + files.length + " test files");
                for (File file : files) {
                    double eta = 0.0; // Log-odds to accumulate for spam probability calculation

                    // Compute eta value based on word probabilities
                    for (String word : parseFile(file)) {
                        double prSWi = wordSpamProbabilities.getOrDefault(word, 0.5);
                        eta += Math.log(1 - prSWi) - Math.log(prSWi);
                    }

                    // Convert eta to spam probability using logistic function
                    double spamProb = 1.0 / (1.0 + Math.exp(eta));

                    // Store the result with the file name, calculated probability, and actual class
                    results.add(new TestFile(file.getName(), spamProb, actualClass));
                }
            }
        }
    }
}
