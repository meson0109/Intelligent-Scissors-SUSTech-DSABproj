import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;
import java.util.List;

public class Main {
    private static JFrame selectionFrame;
    private static JFrame imageFrame;
    public static BufferedImage selectedImage;
    public static double[][] grayMatrix;
    public static double[][] costMatrix;

    private static Point seedPoint = null;
    private static Point currentMousePoint = null;
    private static List<Point> currentPath = new ArrayList<>();
    private static final int MAX_PATH_LENGTH = 100; // 路径最大长度阈值

    public static void main(String[] args) {
        // 创建选择窗口
        createSelectionWindow();

    }

    private static void createSelectionWindow() {
        selectionFrame = new JFrame("选择图片");
        selectionFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        selectionFrame.setSize(400, 200);
        selectionFrame.setLocationRelativeTo(null); // 居中显示

        JPanel panel = new JPanel(new BorderLayout());

        JLabel label = new JLabel("请选择要查看的图片文件", SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);

        JButton selectButton = new JButton("选择图片");
        selectButton.setPreferredSize(new Dimension(150, 40));
        selectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectImage();
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(selectButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        selectionFrame.add(panel);
        selectionFrame.setVisible(true);
    }

    private static void selectImage() {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "图片文件", "jpg", "jpeg", "png", "gif", "bmp");
        fileChooser.setFileFilter(filter);

        int returnValue = fileChooser.showOpenDialog(selectionFrame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = fileChooser.getSelectedFile();
                BufferedImage image = ImageIO.read(selectedFile);

                selectionFrame.setVisible(false);

                selectedImage = image;
                grayMatrix = GrayConverter.convertToGrayMatrix(image);

                // 打印灰度矩阵
                SobelConverter.printMatrix(grayMatrix, "灰度矩阵", 10);

                // 转换为代价矩阵并打印
                costMatrix = SobelConverter.convertToCostMatrix(grayMatrix);
                SobelConverter.printMatrix(costMatrix, "代价矩阵", 10);

                // 创建并显示图片窗口
                showImageWindow(image, selectedFile.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(selectionFrame,
                        "无法加载图片: " + ex.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void showImageWindow(BufferedImage image, String title) {

        imageFrame = new JFrame("Intelligent Scissors - " + title);
        imageFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        imageFrame.setSize(image.getWidth(), image.getHeight());
        imageFrame.setResizable(false);

        ImagePanel imagePanel = new ImagePanel(image);
        imageFrame.add(imagePanel);

        JLabel statusLabel = new JLabel();
        statusLabel.setText(String.format("img size: %d x %d", image.getWidth(), image.getHeight()));
        imageFrame.add(statusLabel, BorderLayout.SOUTH);

        imageFrame.setLocationRelativeTo(null);
        imageFrame.setVisible(true);
    }

    // 自定义面板类，处理绘制和鼠标事件
    static class ImagePanel extends JPanel {
        private BufferedImage displayImage;

        public ImagePanel(BufferedImage image) {
            this.displayImage = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = displayImage.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        seedPoint = e.getPoint();
                        currentPath.clear();
                        currentPath.add(seedPoint);
                        repaint();
                    }
                }
            });

            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (seedPoint != null) {
                        currentMousePoint = e.getPoint();
                        updatePath();
                        repaint();
                    }
                }
            });
        }

        private void updatePath() {
            if (seedPoint == null || currentMousePoint == null) return;

            // 如果路径太长，更新起点为路径中的某个点
            if (currentPath.size() > MAX_PATH_LENGTH) {
                int newStartIndex = currentPath.size() / 2; // 取中间点作为新起点
                seedPoint = currentPath.get(newStartIndex);
                currentPath = new ArrayList<>(currentPath.subList(newStartIndex, currentPath.size()));
            }

            // 使用Dijkstra算法计算从seedPoint到currentMousePoint的最短路径
            java.util.List<Point> newPathSegment = DijkstraAlgorithm.findShortestPath(
                    costMatrix, seedPoint, currentMousePoint);

            if (!newPathSegment.isEmpty()) {
                // 合并路径
                if (!currentPath.isEmpty() && newPathSegment.get(0).equals(currentPath.get(currentPath.size() - 1))) {
                    currentPath.addAll(newPathSegment.subList(1, newPathSegment.size()));
                } else {
                    currentPath = newPathSegment;
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // 绘制原始图片
            g.drawImage(displayImage, 0, 0, null);

            // 复制一份用于绘制路径而不修改原始图片
            BufferedImage pathImage = new BufferedImage(
                    displayImage.getWidth(), displayImage.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = pathImage.createGraphics();
            g2d.drawImage(displayImage, 0, 0, null);

            // 绘制路径
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(2));
            if (currentPath.size() > 1) {
                for (int i = 0; i < currentPath.size() - 1; i++) {
                    Point p1 = currentPath.get(i);
                    Point p2 = currentPath.get(i + 1);
                    g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
            }

            // 绘制起点和当前点
            if (seedPoint != null) {
                g2d.setColor(Color.GREEN);
                g2d.fillOval(seedPoint.x - 3, seedPoint.y - 3, 6, 6);
            }
            if (currentMousePoint != null) {
                g2d.setColor(Color.BLUE);
                g2d.fillOval(currentMousePoint.x - 3, currentMousePoint.y - 3, 6, 6);
            }

            g2d.dispose();
            g.drawImage(pathImage, 0, 0, null);
        }
    }
}

