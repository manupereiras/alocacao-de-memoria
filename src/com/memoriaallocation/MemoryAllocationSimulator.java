package com.memoriaallocation;

import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class MemoryAllocationSimulator extends JFrame {
    private final JComboBox<String> strategyComboBox;
    private final DefaultListModel<String> processListModel;
    private final List<MemoryBlock> memoryBlocks;
    private final List<Process> processes;
    private final JPanel memoryPanel;
    private final JButton allocateButton, resetButton, simulateIOButton;
    private final JTextField nameField, sizeField;
    private final JLabel memoryStatusLabel;
    private final List<Page> pageTable; // Tabela de páginas
    private final List<Process> processWaitingQueue; // Fila de processos esperando alocação
    private int nextFitIndex = 0;

    enum Estado {
        Novo, Pronto, Executando, Bloqueado, Finalizado
    }

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

        pageTable = new ArrayList<>();  // Inicializa a tabela de páginas
        processWaitingQueue = new ArrayList<>(); // Fila de espera de processos

        DefaultListModel<String> statusListModel = new DefaultListModel<>();
        JList<String> statusList = new JList<>(statusListModel);
        add(new JScrollPane(statusList), BorderLayout.WEST);

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
        JButton executeButton = new JButton("Executar Processo");
        processPanel.add(executeButton);

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
            p.estado = Estado.Pronto;

            boolean success = switch (strategy) {
                case "First Fit" -> allocateFirstFit(p);
                case "Best Fit" -> allocateBestFit(p);
                case "Worst Fit" -> allocateWorstFit(p);
                case "Next Fit" -> allocateNextFit(p);
                default -> false;
            };

            if (success) {
                processListModel.addElement(p.toString());
                updateStatusList(statusListModel);
                repaint();
            } else {
                // Aviso de que não há mais memória disponível
                JOptionPane.showMessageDialog(this, "Não há blocos suficientes de memória para alocar o processo. Processo colocado em espera.");

                // Coloca o processo em espera
                processWaitingQueue.add(p); // Adiciona à fila de espera
                p.estado = Estado.Bloqueado; // Marca o estado do processo como "Bloqueado"

                updateStatusList(statusListModel);
                repaint();
            }
            updateMemoryStatus();
        });

        resetButton.addActionListener(e -> {
            processes.clear();
            processListModel.clear();
            memoryBlocks.forEach(MemoryBlock::clear);
            pageTable.clear();  // Limpar tabela de páginas
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
                p.estado = Estado.Bloqueado;
                updateStatusList(statusListModel);
                repaint();
                try {
                    Thread.sleep(3000); // Simula espera E/S
                } catch (InterruptedException ignored) {
                }
                p.blocked = false;
                p.estado = Estado.Executando;
                updateStatusList(statusListModel);
                repaint();
            }).start();
        });


        executeButton.addActionListener(e -> {
            if (processes.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nenhum processo pronto para executar.");
                return;
            }

            Optional<Process> optionalProcess = processes.stream()
                    .filter(p -> p.estado == Estado.Pronto)
                    .findAny();

            if (optionalProcess.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nenhum processo no estado PRONTO.");
                return;
            }

            Process p = optionalProcess.get();
            p.estado = Estado.Executando;
            updateStatusList(statusListModel);
            repaint();

            new Thread(() -> {
                try {
                    Thread.sleep(3000);  // Simula tempo de execução
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

                p.estado = Estado.Finalizado;

                // Libera memória
                for (MemoryBlock block : memoryBlocks) {
                    if (block.process == p) {
                        block.clear();
                    }
                }

                // Remove páginas associadas ao processo
                pageTable.removeIf(pg -> pg.process == p);

                // Atualizações de UI
                SwingUtilities.invokeLater(() -> {
                    updateStatusList(statusListModel);
                    updateMemoryStatus();
                    repaint();

                    // Tenta alocar processos da fila de espera
                    if (!processWaitingQueue.isEmpty()) {
                        Process next = processWaitingQueue.remove(0);
                        next.estado = Estado.Pronto;
                        String strategy = (String) strategyComboBox.getSelectedItem();
                        boolean success = switch (strategy) {
                            case "First Fit" -> allocateFirstFit(next);
                            case "Best Fit" -> allocateBestFit(next);
                            case "Worst Fit" -> allocateWorstFit(next);
                            case "Next Fit" -> allocateNextFit(next);
                            default -> false;
                        };

                        if (success) {
                            processListModel.addElement(next.toString());
                        } else {
                            next.estado = Estado.Bloqueado;
                            processWaitingQueue.add(next); // volta para a fila
                        }

                        updateStatusList(statusListModel);
                        updateMemoryStatus();
                        repaint();
                    }
                });
            }).start();
        });

        // Atualização em tempo real com Timer
        new Timer(1000, e -> repaint()).start(); // atualiza o painel a cada 1 segundo

        pack();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }



    private void updateStatusList(DefaultListModel<String> statusListModel) {
        statusListModel.clear();
        for (Process p : processes) {
            statusListModel.addElement(p.toString());  // Exibe o contador de falhas de página
        }
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
            g.setColor(block.process == null ? Color.LIGHT_GRAY :
                    (block.process.blocked ? Color.ORANGE : Color.GREEN));
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

    // Função para gerenciar falhas de página
    // Função para gerenciar falhas de página
    private void handlePageFault(Process p) {
        p.pageFaultCount++;  // Incrementa o contador de falhas de página
        System.out.println("Falha de página no processo " + p.name);

        // Verifica se há espaço na tabela de páginas
        if (pageTable.size() < 10) {  // Supondo que temos um número máximo de 10 páginas na memória
            // Carrega a nova página
            pageTable.add(new Page(pageTable.size(), p));
        } else {
            // Substituição de página (FIFO)
            Page pageToRemove = pageTable.remove(0);  // Remove a página mais antiga
            System.out.println("Substituindo: " + pageToRemove);
            pageTable.add(new Page(pageTable.size(), p));  // Adiciona a nova página

            // Aqui podemos implementar outras estratégias de substituição, como LRU
        }
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

    class Process {
        String name;
        int size;
        boolean blocked = false;
        Estado estado;
        int priority;
        int pageFaultCount = 0;  // Contador de falhas de página

        Process(String name, int size) {
            this.name = name;
            this.size = size;
            this.estado = Estado.Novo;
            this.priority = new Random().nextInt(10) + 1;
        }

        public String toString() {
            return name + " (" + size + "KB)" + "[" + estado + "]" + " Prioridade: " + priority + ", Falhas de Página: " + pageFaultCount;
        }

        // Simula o acesso a uma página
        public void accessPage(int pageId) {
            // Verifica se a página está na memória
            boolean pageFound = false;
            for (Page p : pageTable) {
                if (p.process == this && p.id == pageId) {
                    pageFound = true;
                    break;
                }
            }

            // Se não encontrar a página, é uma falha de página
            if (!pageFound) {
                handlePageFault(this);  // Chama a função de falha de página
            }
        }
    }


    static class Page {
        int id;
        Process process;
        long timestamp;  // Para LRU (opcional, pode ser usado para tempo de acesso)

        Page(int id, Process process) {
            this.id = id;
            this.process = process;
            this.timestamp = System.currentTimeMillis();  // Captura o momento de criação
        }

        public String toString() {
            return "Página " + id + " do Processo " + process.name;
        }
    }

}
