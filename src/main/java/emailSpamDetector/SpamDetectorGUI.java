package emailSpamDetector;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.InputStream;


public class SpamDetectorGUI {
    public static void main(String[] args) {
        // Set the systems default font style to "Comic Sans MS"
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            Font font = new Font("Comic Sans MS", Font.PLAIN, 14); // Choose your desired font, style, and size
            UIManager.put("Label.font", font);
            UIManager.put("Button.font", font);
            UIManager.put("Table.font", font);
            UIManager.put("TableHeader.font", font);
            UIManager.put("TextArea.font", font);
            UIManager.put("TextField.font", font);
            UIManager.put("ComboBox.font", font);
            UIManager.put("MenuBar.font", font);
            UIManager.put("MenuItem.font", font);
            UIManager.put("Menu.font", font);
            UIManager.put("Panel.font", font);
            UIManager.put("Window.font", font);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create and display the GUI
        SwingUtilities.invokeLater(() -> {
            // Create the main window
            JFrame window = new JFrame("Spam Detector");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setSize(800, 600);

            // Create a button for selecting directory
            JButton selectDirBtn = new JButton("Select Directory");

            // Create a table to display results
            JTable resultsTable = new JTable();
            JLabel accuracyLabel = new JLabel("Accuracy: ");
            JLabel precisionLabel = new JLabel("Precision: ");

            // Action listener for the "select directory" button
            selectDirBtn.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setCurrentDirectory(new File("."));

                // When user selects a directory, process its test files
                if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                    File mainDir = chooser.getSelectedFile();
                    SpamDetector detector = new SpamDetector(mainDir);
                    processTestFiles(window, detector, mainDir, resultsTable, accuracyLabel, precisionLabel);
                }
            });

            // Create a panel to display accuracy and precision
            JPanel bottomPanel = new JPanel(new GridLayout(2, 1));
            bottomPanel.add(accuracyLabel);
            bottomPanel.add(precisionLabel);

            // Set a layout of the window and add components
            window.setLayout(new BorderLayout());
            window.add(selectDirBtn, BorderLayout.NORTH); // Add button to top
            window.add(new JScrollPane(resultsTable), BorderLayout.CENTER); // Add table to center
            window.add(bottomPanel, BorderLayout.SOUTH); // Add the bottom panel to bottom
            window.setVisible(true); // Make the window visible
        });
    }

    // Processes files within the directory
    private static void processTestFiles(JFrame window, SpamDetector detector, File mainDir, JTable table, JLabel accuracyLabel, JLabel precisionLabel) {
        // Create list of test files to process
        List<TestFile> testFiles = detector.test(mainDir);

        // Create results table
        DefaultTableModel model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make cells non-editable
            }
        };

        // Add appropriate columns to table
        model.addColumn("File");
        model.addColumn("Actual Class");
        model.addColumn("Spam Probability");

        // Initialize variables for calculations
        int correct = 0, truePositives = 0, falsePositives = 0;

        // Loop through each test file and process its spam detection results
        for (TestFile tf : testFiles) {
            String actual = tf.getActualClass();
            // Classify as Spam if probability is >= 0.6, otherwise Ham
            String predicted = tf.getSpamProbability() >= 0.6 ? "Spam" : "Ham"; // modified 0.5 to 0.6
            boolean isCorrect = actual.equalsIgnoreCase(predicted);

            // Update correct counter based on classification results for later calculations
            if (isCorrect) {
                correct++;
                if ("Spam".equalsIgnoreCase(predicted)) truePositives++;
            } else if ("Spam".equalsIgnoreCase(predicted)) {
                falsePositives++;
            }

            model.addRow(new Object[]{tf.getFilename(), actual, tf.getSpamProbRounded()});
        }

        // Calculate accuracy and precision
        double accuracy = (double) correct / testFiles.size();
        double precision = (double) truePositives / (truePositives + falsePositives);

        // Update accuracy and precision labels
        accuracyLabel.setText(String.format("Accuracy: %.5f", accuracy));
        precisionLabel.setText(String.format("Precision: %.5f", precision));
        table.setModel(model);

        // Set custom cell renders for columns to colour results
        table.getColumnModel().getColumn(1).setCellRenderer(new SpamCellRenderer());
        table.getColumnModel().getColumn(2).setCellRenderer(new SpamCellRenderer());

        // Add mouse listener to open emails when clicking file names
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Get the selected row and column
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());

                // Check if the click was on a valid row and column
                if (row >= 0 && column == 0) {  // Only act if the File column is clicked
                    String filename = (String) table.getValueAt(row, column);

                    // Open the file and read its content
                    String fileContent = readFileContent(filename, mainDir);

                    if (fileContent != null) {
                        // Display the file contents in a new window
                        showFileContentWindow(window, filename, fileContent);
                    } else {
                        JOptionPane.showMessageDialog(window, "Unable to read the file.");
                    }
                }
            }
        });
    }

    // Read the content of a file
    private static String readFileContent(String filename, File mainDir) {
        try {
            // Define paths for ham and spam directories
            String filePathHam = "data/test/ham/" + filename;
            String filePathSpam = "data/test/spam/" + filename;

            // Try to load the file from the "ham" directory
            InputStream fileStream = SpamDetectorGUI.class.getClassLoader().getResourceAsStream(filePathHam);

            // If not found, try loading from the "spam" directory
            if (fileStream == null) {
                fileStream = SpamDetectorGUI.class.getClassLoader().getResourceAsStream(filePathSpam);
            };

            // If fileStream is not null, read the file content
            if (fileStream != null) {
                return new String(fileStream.readAllBytes());
            } else {
                // Handle the case where the file was not found in the resources folder
                System.out.println("File not found: " + filePathHam + "or" + filePathSpam);
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Create a new window and display file content
    private static void showFileContentWindow(JFrame window, String filename, String content) {
        JFrame fileWindow = new JFrame("File Content - " + filename);
        fileWindow.setSize(600, 400); // Set size of window

        JTextArea textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);

        JScrollPane scrollPane = new JScrollPane(textArea);
        fileWindow.add(scrollPane, BorderLayout.CENTER);

        fileWindow.setVisible(true);
    }

    // Custom table cell renderer for spam and ham results
    static class SpamCellRenderer extends JLabel implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value != null ? value.toString() : "");
            if (column == 1) { // Check if we are in the "Actual Class" column
                // Set the background color for spam (red) or ham (green)
                if ("Spam".equalsIgnoreCase(value.toString())) {
                    setBackground(Color.RED);
                } else if ("Ham".equalsIgnoreCase(value.toString())) {
                    setBackground(Color.GREEN);
                } else {
                    // Default to white if no valid value
                    setBackground(Color.WHITE);
                }
            }
            else if (column == 2) { // Check if we are in the "Spam Probability" column
                // Check if value is a String
                if (value != null && value instanceof String) {
                    try {
                        // Try to parse the String to a double
                        double probability = Double.parseDouble((String) value);

                        // If probability is greater than threshold, set colour to red, otherwise green
                        if (probability >= 0.6) {
                            setBackground(Color.RED);
                        } else {
                            setBackground(Color.GREEN);
                        }
                    } catch (NumberFormatException e) {
                        // Handle the case where the String cannot be parsed to a double
                        System.out.println("Error parsing value to double: " + value);
                        setBackground(Color.WHITE);  // Default background if parsing fails
                    }
                } else {
                    // If it's not a String or a valid Number, print out a message
                    System.out.println("Unexpected value type: " + (value != null ? value.getClass().getName() : "null"));
                }
            }

            setForeground(Color.BLACK);  // Set text color to black for all rows
            setOpaque(true);  // Make the background color visible
            return this;
        }
    }
}
