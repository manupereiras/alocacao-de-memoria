package com.memoriaallocation;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MemoryAllocationSimulator extends JFrame {
    private final JComboBox<String> strategyComboBox;
    private final DefaultListModel<String> processListModel;
    private final List<MemoryBlock> memoryBlocks;
    private final List<Process> processes;
    private final JPanel memoryPanel;
    private final JButton allocateButton, resetButton, simulateIOButton;
    private final JTextField nameField, sizeField;
    private final JLabel memoryStatusLabel;
    private int nextFitIndex = 0;

    public MemoryAllocationSimulator() {
        super("Simulador de Alocação de Memória");
        setLayout(new BorderLayout());

        strategyComboBox = new JComboBox<>(new String[]{
                "First Fit", "Best Fit", "Worst Fit", "Next Fit"
        });

        processListModel = new DefaultListModel<>();
        processes = new ArrayList<>();
        memoryBlocks = new ArrayList<>(Arrays.asList(
                new MemoryBlock(0, 100),
                new MemoryBlock(1, 150),
                new MemoryBlock(2, 200),
                new MemoryBlock(3, 250),
                new MemoryBlock(4, 300),
                new MemoryBlock(5, 350)
        ));

        // Painel de Entrada
        JPanel inputPanel = new JPanel(new GridLayout(2, 1));
        JPanel processPanel = new JPanel();
        processPanel.add(new JLabel("Nome:"));
        nameField = new JTextField(5);
        processPanel.add(nameField);
        processPanel.add(new JLabel("Tamanho:"));
        sizeField = new JTextField(5);
        processPanel.add(sizeField);
        allocateButton = new JButton("Alocar");
        processPanel.add(allocateButton);
        resetButton = new JButton("Reiniciar");
        processPanel.add(resetButton);
        simulateIOButton = new JButton("Simular E/S bloqueante");
        processPanel.add(simulateIOButton);
        inputPanel.add(processPanel);

        JPanel strategyPanel = new JPanel();
        strategyPanel.add(new JLabel("Estratégia:"));
        strategyPanel.add(strategyComboBox);
        inputPanel.add(strategyPanel);

        add(inputPanel, BorderLayout.NORTH);

        // Painel de Memória
        memoryPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawMemoryBlocks(g);
            }
        };
        memoryPanel.setPreferredSize(new Dimension(600, 400));
        add(memoryPanel, BorderLayout.CENTER);

        // Lista de Processos
        JList<String> processList = new JList<>(processListModel);
        add(new JScrollPane(processList), BorderLayout.EAST);

        // Exibição do Status de Memória
        memoryStatusLabel = new JLabel("Memória: Total: 0KB | Ocupados: 0KB | Livre: 0KB");
        add(memoryStatusLabel, BorderLayout.SOUTH);

        // Ações
        allocateButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            int size;
            try {
                size = Integer.parseInt(sizeField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Tamanho Inválido");
                return;
            }

            String strategy = (String) strategyComboBox.getSelectedItem();
            Process p = new Process(name, size);
            processes.add(p);

            boolean success = switch (strategy) {
                case "First Fit" -> allocateFirstFit(p);
                case "Best Fit" -> allocateBestFit(p);
                case "Worst Fit" -> allocateWorstFit(p);
                case "Next Fit" -> allocateNextFit(p);
                default -> false;
            };

            if (success) {
                processListModel.addElement(p.toString());
                repaint();
            } else {
                JOptionPane.showMessageDialog(this, "Não foi possível alocar o processo.");
            }
            updateMemoryStatus();
        });

        resetButton.addActionListener(e -> {
            processes.clear();
            processListModel.clear();
            memoryBlocks.forEach(MemoryBlock::clear);
            nextFitIndex = 0;
            updateMemoryStatus();
            repaint();
        });

        simulateIOButton.addActionListener(e -> {
            if (processes.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nenhum processo em execução");
                return;
            }
            new Thread(() -> {
                Process p = processes.get(new Random().nextInt(processes.size()));
                p.blocked = true;
                repaint();
                try {
                    Thread.sleep(3000); // Simula espera E/S
                } catch (InterruptedException ignored) {
                }
                p.blocked = false;
                repaint();
            }).start();
        });

        pack();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private boolean allocateFirstFit(Process p) {
        for (MemoryBlock block : memoryBlocks) {
            if (block.isFree() && block.size >= p.size) {
                block.allocate(p);
                return true;
            }
        }
        return false;
    }

    private boolean allocateBestFit(Process p) {
        MemoryBlock best = null;
        for (MemoryBlock block : memoryBlocks) {
            if (block.isFree() && block.size >= p.size) {
                if (best == null || block.size < best.size) {
                    best = block;
                }
            }
        }
        if (best != null) {
            best.allocate(p);
            return true;
        }
        return false;
    }

    private boolean allocateWorstFit(Process p) {
        MemoryBlock worst = null;
        for (MemoryBlock block : memoryBlocks) {
            if (block.isFree() && block.size >= p.size) {
                if (worst == null || block.size > worst.size) {
                    worst = block;
                }
            }
        }
        if (worst != null) {
            worst.allocate(p);
            return true;
        }
        return false;
    }

    private boolean allocateNextFit(Process p) {
        int n = memoryBlocks.size();
        for (int i = 0; i < n; i++) {
            int index = (nextFitIndex + i) % n;
            MemoryBlock block = memoryBlocks.get(index);
            if (block.isFree() && block.size >= p.size) {
                block.allocate(p);
                nextFitIndex = (index + 1) % n;
                return true;
            }
        }
        return false;
    }

    private void drawMemoryBlocks(Graphics g) {
        int y = 20;
        for (MemoryBlock block : memoryBlocks) {
            g.setColor(block.process == null ? Color.LIGHT_GRAY : (
                    block.process.blocked ? Color.ORANGE : Color.GREEN));
            g.fillRect(50, y, 200, 40);
            g.setColor(Color.BLACK);
            g.drawRect(50, y, 200, 40);
            g.drawString("Bloco " + block.id + ": " + block.size + "KB", 60, y + 15);
            if (block.process != null) {
                g.drawString(block.process.name + " (" + block.process.size + "KB)", 60, y + 35);
            }
            y += 60;
        }
    }

    private void updateMemoryStatus() {
        int totalMemory = 0;
        int usedMemory = 0;
        for (MemoryBlock block : memoryBlocks) {
            totalMemory += block.size;
            if (block.process != null) {
                usedMemory += block.size;
            }
        }
        int freeMemory = totalMemory - usedMemory;
        memoryStatusLabel.setText("Memória: Total: " + totalMemory + "KB | Ocupados: " + usedMemory + "KB | Livre: " + freeMemory + "KB");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MemoryAllocationSimulator::new);
    }

    // Classes Auxiliares
    static class MemoryBlock {
        int id, size;
        Process process;

        MemoryBlock(int id, int size) {
            this.id = id;
            this.size = size;
        }

        boolean isFree() {
            return process == null;
        }

        void allocate(Process p) {
            this.process = p;
        }

        void clear() {
            this.process = null;
        }
    }

    static class Process {
        String name;
        int size;
        boolean blocked = false;

        Process(String name, int size) {
            this.name = name;
            this.size = size;
        }

        public String toString() {
            return name + " (" + size + "KB)" + (blocked ? " [BLOQUEADO]" : "");
        }
    }
}