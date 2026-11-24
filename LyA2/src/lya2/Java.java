package lya2;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.tools.*;

public class Java extends JFrame {
    private JTextPane codeArea;
    private JTable tokensTable, symbolsTable, quadruplesTable, triplesTable, errorsTable;
    private DefaultTableModel tokensTableModel, symbolsTableModel, quadruplesTableModel, triplesTableModel, errorsTableModel;
    private JTextArea outputArea, assemblyArea;
    private ASTPanel astPanel;
    private SymbolTable symbolTable;
    private List<Quadruple> quadruples;
    private List<Triple> triples;
    private List<SyntaxError> syntaxErrors;
    private int tempCounter, memoryAddress;
    private String executionResult;
    
    private final String[] keywords = {
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
        "int", "interface", "long", "native", "new", "package", "private", "protected",
        "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
        "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
    };
    
    // --- Clases de Soporte ---
    static class SymbolEntry {
        String name, type, scope, value, symbolType;
        int memoryAddress, size, line;
        boolean isUsed, isInitialized, isConstant, hasReturn;
        
        public SymbolEntry(String name, String type, String scope, int memoryAddress, int size, int line) {
            this.name = name; this.type = type; this.scope = scope;
            this.memoryAddress = memoryAddress; this.size = size; this.line = line;
            this.value = "null"; this.isUsed = false; this.isInitialized = false;
            this.isConstant = false; this.symbolType = determineSymbolType(type); this.hasReturn = false;
        }
        private String determineSymbolType(String type) {
            if (type.equals("method")) return "Método";
            if (type.equals("class")) return "Clase";
            return "Variable";
        }
        public void setValue(String value) { this.value = value; this.isInitialized = true; }
        public void setUsed(boolean used) { this.isUsed = used; }
        public void setConstant(boolean constant) { this.isConstant = constant; }
        public void setHasReturn(boolean hasReturn) { this.hasReturn = hasReturn; }
        public String getMemoryAddressHex() { return String.format("0x%04X", memoryAddress); }
    }
    
    static class SymbolTable {
        Map<String, SymbolEntry> entries = new HashMap<>();
        int currentAddress = 1000;
        public void addSymbol(String name, String type, String scope, int size, int line) {
            if (!entries.containsKey(name + "_" + scope)) {
                entries.put(name + "_" + scope, new SymbolEntry(name, type, scope, currentAddress, size, line));
                currentAddress += size;
            }
        }
        public SymbolEntry getSymbol(String name, String scope) { return entries.get(name + "_" + scope); }
        public List<SymbolEntry> getAllSymbols() { return new ArrayList<>(entries.values()); }
    }
    
    static class Quadruple {
        String operator, arg1, arg2, result;
        public Quadruple(String operator, String arg1, String arg2, String result) {
            this.operator = operator; this.arg1 = arg1; this.arg2 = arg2; this.result = result;
        }
    }
    
    static class Triple {
        String operator, operand, result;
        public Triple(String operator, String operand, String result) {
            this.operator = operator; this.operand = operand; this.result = result;
        }
    }
    
    static class SyntaxError {
        int line; String description, token;
        public SyntaxError(int line, String description, String token) {
            this.line = line; this.description = description; this.token = token;
        }
    }

    // --- Constructor Principal ---
    public Java() {
        super("Analizador Léxico, Sintáctico y AST Java - Con Tabla de Símbolos y Cuádruplos");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        initializeComponents();
        setupLayoutAndEvents();
    }
    
    private void initializeComponents() {
        codeArea = new JTextPane();
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        
        tokensTableModel = new DefaultTableModel(new Object[]{"Línea", "Tipo", "Token", "Valor", "Estado"}, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        tokensTable = new JTable(tokensTableModel);
        
        symbolsTableModel = new DefaultTableModel(new Object[]{"Nombre", "Tipo", "Valor", "Línea", "Ámbito", "Usado", "Dirección Mem", "Inicializado", "Constante", "Tipo Símbolo", "Retorno", "Estado"}, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        symbolsTable = new JTable(symbolsTableModel);
        
        quadruplesTableModel = new DefaultTableModel(new Object[]{"Operador", "Arg1", "Arg2", "Resultado"}, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        quadruplesTable = new JTable(quadruplesTableModel);
        
        triplesTableModel = new DefaultTableModel(new Object[]{"Operador", "Operando", "Resultado"}, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        triplesTable = new JTable(triplesTableModel);
        
        errorsTableModel = new DefaultTableModel(new Object[]{"Línea", "Error", "Token"}, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        errorsTable = new JTable(errorsTableModel);
        
        outputArea = new JTextArea(10, 40); outputArea.setEditable(false); outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        assemblyArea = new JTextArea(10, 40); assemblyArea.setEditable(false); assemblyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        
        astPanel = new ASTPanel();
        
        symbolTable = new SymbolTable();
        quadruples = new ArrayList<>(); triples = new ArrayList<>(); syntaxErrors = new ArrayList<>();
        tempCounter = 0; memoryAddress = 1000; executionResult = "";
    }
    
    // --- NUEVO DISEÑO DE INTERFAZ Y EVENTOS ---
    private void setupLayoutAndEvents() {
        // 1. Panel de Código (Izquierda)
        JScrollPane codeScroll = new JScrollPane(codeArea);
        codeScroll.setBorder(BorderFactory.createTitledBorder("Código Java"));
        
        // 2. Panel de Tablas (Derecha)
        JTabbedPane tablesTabbedPane = new JTabbedPane();
        tablesTabbedPane.addTab("Tokens", new JScrollPane(tokensTable));
        tablesTabbedPane.addTab("Símbolos", new JScrollPane(symbolsTable));
        tablesTabbedPane.addTab("Cuádruplos", new JScrollPane(quadruplesTable));
        tablesTabbedPane.addTab("Triplos", new JScrollPane(triplesTable));
        tablesTabbedPane.addTab("Errores", new JScrollPane(errorsTable));
        
        // 3. Panel de Salida y Ensamblador (Abajo - Ocupará todo el ancho)
        JTabbedPane outputTabbedPane = new JTabbedPane();
        outputTabbedPane.addTab("Salida", new JScrollPane(outputArea));
        outputTabbedPane.addTab("Ensamblador", new JScrollPane(assemblyArea));
        
        // --- DIVISORES (SPLITS) ---
        // Divisor Superior (Código a la izquierda, Tablas a la derecha)
        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, codeScroll, tablesTabbedPane);
        topSplit.setResizeWeight(0.4); // 40% para código, 60% para tablas
        
        // Divisor Principal (Arriba el topSplit, Abajo la Salida a todo lo ancho)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, outputTabbedPane);
        mainSplit.setResizeWeight(0.7); // 70% arriba, 30% abajo
        
        // --- BOTONES ---
        JButton loadButton = new JButton("Cargar archivo");
        JButton analyzeButton = new JButton("Analizar");
        JButton astButton = new JButton("Generar arbol"); // EL BOTÓN SOLICITADO
        JButton runButton = new JButton("Ejecutar código");
        
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(loadButton);
        buttonsPanel.add(analyzeButton);
        buttonsPanel.add(astButton);
        buttonsPanel.add(runButton);
        
        // --- EVENTOS DE LOS BOTONES ---
        loadButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    codeArea.setText(new String(Files.readAllBytes(chooser.getSelectedFile().toPath())));
                    clearAllTables();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error al leer: " + ex.getMessage());
                }
            }
        });
        
        analyzeButton.addActionListener(e -> {
            clearAllTables();
            analyzeCode(codeArea.getText());
        });
        
        // --- LÓGICA DE LA NUEVA VENTANA DEL ÁRBOL ---
        astButton.addActionListener(e -> {
            // Si no hay árbol generado, analiza primero
            if (astPanel.root == null) {
                analyzeCode(codeArea.getText());
            }
            
            // Crear la nueva ventana independiente
            JFrame treeWindow = new JFrame("Árbol de Sintaxis Abstracta (AST)");
            treeWindow.setSize(900, 700);
            treeWindow.setLocationRelativeTo(this);
            treeWindow.setLayout(new BorderLayout());
            
            // Agregar el panel del árbol en el centro
            treeWindow.add(new JScrollPane(astPanel), BorderLayout.CENTER);
            
            // Crear panel inferior con el botón de regresar
            JPanel bottomPanel = new JPanel();
            JButton returnButton = new JButton("Regresar");
            returnButton.setFont(new Font("Arial", Font.BOLD, 14));
            
            // Acción para cerrar la ventana del árbol al presionar "Regresar"
            returnButton.addActionListener(evt -> treeWindow.dispose());
            
            bottomPanel.add(returnButton);
            treeWindow.add(bottomPanel, BorderLayout.SOUTH);
            
            treeWindow.setVisible(true);
        });
        
        runButton.addActionListener(e -> {
            outputArea.setText("Compilando y ejecutando...\n");
            runJavaCode(codeArea.getText());
        });
        
        // --- AGREGAR TODO A LA VENTANA PRINCIPAL ---
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(buttonsPanel, BorderLayout.NORTH);
        getContentPane().add(mainSplit, BorderLayout.CENTER);
    }
    
    private void clearAllTables() {
        tokensTableModel.setRowCount(0); symbolsTableModel.setRowCount(0);
        quadruplesTableModel.setRowCount(0); triplesTableModel.setRowCount(0);
        errorsTableModel.setRowCount(0); assemblyArea.setText("");
        astPanel.setAST(null); symbolTable = new SymbolTable();
        quadruples.clear(); triples.clear(); syntaxErrors.clear();
        tempCounter = 0; memoryAddress = 1000; executionResult = "";
    }
    
    private void analyzeCode(String code) {
        tokenizeAndFillTable(code);
        parseJavaProgram(code);
        generateIntermediateCode(code);
        generateAssemblyCode();
        
        Set<Integer> errorLines = getAllErrorLines();
        setupTableRenderers(errorLines);
        updateSymbolsTableWithErrors(errorLines);
        updateQuadruplesTable(); updateTriplesTable(); updateErrorsTable();
        updateTokensWithErrors(errorLines);
        displayAnalysisSummary();
        astPanel.repaint();
    }
    
    private void setupTableRenderers(Set<Integer> errorLines) {
        DefaultTableCellRenderer errorRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                Integer lineNumber = 0;
                try {
                    if (table == tokensTable || table == errorsTable) lineNumber = (Integer) table.getModel().getValueAt(row, 0);
                    else if (table == symbolsTable) lineNumber = (Integer) table.getModel().getValueAt(row, 3);
                } catch (Exception ignored) {}

                if (errorLines.contains(lineNumber) || table == errorsTable) {
                    c.setBackground(new Color(255, 200, 200)); c.setForeground(Color.BLACK);
                } else if (isSelected) {
                    c.setBackground(table.getSelectionBackground()); c.setForeground(table.getSelectionForeground());
                } else {
                    c.setBackground(table.getBackground()); c.setForeground(table.getForeground());
                }
                return c;
            }
        };
        tokensTable.setDefaultRenderer(Object.class, errorRenderer);
        symbolsTable.setDefaultRenderer(Object.class, errorRenderer);
        errorsTable.setDefaultRenderer(Object.class, errorRenderer);
    }
    
    private Set<Integer> getAllErrorLines() {
        Set<Integer> errorLines = new HashSet<>();
        for (SyntaxError error : syntaxErrors) errorLines.add(error.line);
        for (int i = 0; i < tokensTableModel.getRowCount(); i++) {
            if ("DESCONOCIDO".equals(tokensTableModel.getValueAt(i, 1))) {
                errorLines.add((Integer) tokensTableModel.getValueAt(i, 0));
            }
        }
        return errorLines;
    }
    
    private void updateTokensWithErrors(Set<Integer> errorLines) {
        for (int i = 0; i < tokensTableModel.getRowCount(); i++) {
            tokensTableModel.setValueAt(errorLines.contains((Integer) tokensTableModel.getValueAt(i, 0)) ? "ERROR" : "OK", i, 4);
        }
    }
    
    private void displayAnalysisSummary() {
        StringBuilder summary = new StringBuilder("=== RESUMEN DEL ANÁLISIS ===\n\n");
        summary.append("TOKENS IDENTIFICADOS: ").append(tokensTableModel.getRowCount()).append("\n");
        summary.append("SÍMBOLOS EN TABLA: ").append(symbolsTableModel.getRowCount()).append("\n");
        summary.append("\nCÓDIGO INTERMEDIO GENERADO:\n");
        summary.append("   - Cuádruplos: ").append(quadruplesTableModel.getRowCount()).append("\n");
        summary.append("   - Triplos: ").append(triplesTableModel.getRowCount()).append("\n");
        if (syntaxErrors.isEmpty()) {
            summary.append("\nNO SE ENCONTRARON ERRORES SINTÁCTICOS\n");
        } else {
            summary.append("\nERRORES SINTÁCTICOS ENCONTRADOS: ").append(syntaxErrors.size()).append("\n");
        }
        outputArea.setText(summary.toString());
    }
    
    private void tokenizeAndFillTable(String code) {
        String[] lines = code.split("\n");
        int lineNumber = 1;
        for (String line : lines) {
            Pattern tokenPattern = Pattern.compile("(\"(\\\\.|[^\"\\\\])*\"|'\\\\?.'|\\b\\d+\\.?\\d*\\b|\\b\\w+\\b|\\S)");
            Matcher matcher = tokenPattern.matcher(line);
            while (matcher.find()) {
                String token = matcher.group();
                tokensTableModel.addRow(new Object[]{lineNumber, classifyToken(token), token, getTokenValue(token, classifyToken(token)), "OK"});
            }
            lineNumber++;
        }
    }
    
    private String classifyToken(String token) {
        if (Arrays.asList(keywords).contains(token)) return "PALABRA_CLAVE";
        if (token.matches("\"(\\\\.|[^\"\\\\])*\"|'\\\\?.'")) return "LITERAL";
        if (token.matches("\\b\\d+\\.?\\d*\\b")) return "NUMERO";
        if (token.matches("[a-zA-Z_][a-zA-Z0-9_]*")) return "IDENTIFICADOR";
        if (token.matches("[{}()\\[\\];,\\.+\\-*/=<>!&|%^~?:]")) return "SIMBOLO";
        return "DESCONOCIDO";
    }
    
    private String getTokenValue(String token, String type) {
        switch (type) {
            case "PALABRA_CLAVE": return "Palabra reservada";
            case "LITERAL": return "Valor literal";
            case "NUMERO": return "Valor numérico";
            case "IDENTIFICADOR": return "Nombre de variable/función";
            case "SIMBOLO": return "Símbolo del lenguaje";
            default: return "Token no reconocido";
        }
    }
    
    private void parseJavaProgram(String code) {
        ASTNode root = new ASTNode("Programa");
        
        Matcher classMatcher = Pattern.compile("\\bclass\\s+(\\w+)").matcher(code);
        if (classMatcher.find()) {
            String className = classMatcher.group(1);
            ASTNode classNode = new ASTNode("Clase: " + className);
            symbolTable.addSymbol(className, "class", "global", 4, 1);
            root.addChild(classNode);
            
            extractVariablesAndMethods(code, classNode);
            checkSyntax(code);
            
            if (!syntaxErrors.isEmpty()) {
                ASTNode errorsNode = new ASTNode("ERRORES DETECTADOS");
                root.addChild(errorsNode);
            }
        } else {
            root.addChild(new ASTNode("No se encontro clase principal"));
        }
        
        astPanel.setAST(root);
    }
    
    private void extractVariablesAndMethods(String code, ASTNode classNode) {
        Matcher varInitMatcher = Pattern.compile("(int|double|float|String|boolean)\\s+(\\w+)\\s*=\\s*([^;]+);").matcher(code);
        while (varInitMatcher.find()) {
            String type = varInitMatcher.group(1), name = varInitMatcher.group(2), value = varInitMatcher.group(3).trim();
            symbolTable.addSymbol(name, type, "local", 4, 1);
            classNode.addChild(new ASTNode("Variable: " + type + " " + name + " = " + value));
        }
        
        Matcher methodMatcher = Pattern.compile("(public|private|protected|static|\\s)+(void|int|double|float|String|boolean)\\s+(\\w+)\\s*\\(").matcher(code);
        while (methodMatcher.find()) {
            symbolTable.addSymbol(methodMatcher.group(3), "method", "local", 8, 1);
            classNode.addChild(new ASTNode("Método: " + methodMatcher.group(2) + " " + methodMatcher.group(3)));
        }
    }
    
    private void checkSyntax(String code) {
        String[] lines = code.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("//") || line.endsWith("{") || line.endsWith("}")) continue;
            if (line.matches(".*(int|double|String|boolean)\\s+\\w+(\\s*=\\s*[^;]+)?$") && !line.endsWith(";")) {
                syntaxErrors.add(new SyntaxError(i + 1, "Falta punto y coma", line));
            }
        }
    }
    
    private void generateIntermediateCode(String code) {
        quadruples.clear(); triples.clear();
        Matcher exprMatcher = Pattern.compile("(\\w+)\\s*=\\s*([^;]+);").matcher(code);
        while (exprMatcher.find()) {
            String resultVar = exprMatcher.group(1), expression = exprMatcher.group(2).trim();
            if (expression.contains("+")) {
                String[] parts = expression.split("\\+");
                if(parts.length == 2) {
                    String temp = "t" + (++tempCounter);
                    quadruples.add(new Quadruple("+", parts[0].trim(), parts[1].trim(), temp));
                    quadruples.add(new Quadruple(":=", temp, "", resultVar));
                    triples.add(new Triple("+", parts[0].trim() + "," + parts[1].trim(), temp));
                    triples.add(new Triple(":=", temp, resultVar));
                }
            } else {
                quadruples.add(new Quadruple(":=", expression, "", resultVar));
                triples.add(new Triple(":=", expression, resultVar));
            }
        }
        
        Matcher whileMatcher = Pattern.compile("while\\s*\\(([^)]+)\\)").matcher(code);
        while (whileMatcher.find()) {
            String startLabel = "L" + (++tempCounter), endLabel = "L" + (++tempCounter);
            quadruples.add(new Quadruple("label", startLabel, "", "")); quadruples.add(new Quadruple("if_false", whileMatcher.group(1).trim(), "", endLabel));
            quadruples.add(new Quadruple("goto", startLabel, "", "")); quadruples.add(new Quadruple("label", endLabel, "", ""));
            triples.add(new Triple("label", startLabel, "")); triples.add(new Triple("if_false", whileMatcher.group(1).trim(), endLabel));
            triples.add(new Triple("goto", startLabel, "")); triples.add(new Triple("label", endLabel, ""));
        }
    }
    
    private void generateAssemblyCode() {
        StringBuilder asm = new StringBuilder("; Código Ensamblador Generado\nsection .data\n");
        for (Quadruple q : quadruples) {
            asm.append("    ; ").append(q.operator).append(" ").append(q.arg1).append("\n");
        }
        assemblyArea.setText(asm.toString());
    }
    
    private void updateSymbolsTableWithErrors(Set<Integer> errorLines) {
        for (SymbolEntry s : symbolTable.getAllSymbols()) {
            symbolsTableModel.addRow(new Object[]{s.name, s.type, s.value, s.line, s.scope, s.isUsed?"Si":"No", s.getMemoryAddressHex(), s.isInitialized?"Si":"No", s.isConstant?"Si":"No", s.symbolType, s.hasReturn?"Si":"No", errorLines.contains(s.line)?"ERROR":"OK"});
        }
    }
    
    private void updateQuadruplesTable() {
        for (Quadruple q : quadruples) quadruplesTableModel.addRow(new Object[]{q.operator, q.arg1, q.arg2, q.result});
    }
    
    private void updateTriplesTable() {
        for (Triple t : triples) triplesTableModel.addRow(new Object[]{t.operator, t.operand, t.result});
    }
    
    private void updateErrorsTable() {
        for (SyntaxError e : syntaxErrors) errorsTableModel.addRow(new Object[]{e.line, e.description, e.token});
    }
    
    private void runJavaCode(String code) {
        String className = "TempJavaCode";
        Matcher m = Pattern.compile("public\\s+class\\s+(\\w+)").matcher(code);
        if (m.find()) className = m.group(1);
        try {
            File sourceFile = new File(System.getProperty("java.io.tmpdir"), className + ".java");
            Files.write(sourceFile.toPath(), code.getBytes());
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) { outputArea.setText("No se encontró compilador de Java (JDK)."); return; }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (compiler.run(null, out, out, sourceFile.getPath()) != 0) {
                outputArea.setText("Error de compilación:\n" + out.toString()); return;
            }
            Process p = new ProcessBuilder("java", "-cp", System.getProperty("java.io.tmpdir"), className).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder res = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) res.append(line).append("\n");
            p.waitFor(); outputArea.setText(res.toString()); sourceFile.delete();
        } catch (Exception ex) { outputArea.setText("Error: " + ex.getMessage()); }
    }
    
    // --- CLASES PARA DIBUJAR EL ÁRBOL ---
    static class ASTNode {
        String label; List<ASTNode> children = new ArrayList<>();
        int x, y, width, height;
        public ASTNode(String label) { this.label = label; }
        public void addChild(ASTNode node) { children.add(node); }
        public List<ASTNode> getChildren() { return children; }
    }
    
    static class ASTPanel extends JPanel {
        ASTNode root;
        private final int N_W = 160, N_H = 40, V_S = 80, H_S = 30;
        private int maxX = 0, maxY = 0;
        
        public void setAST(ASTNode root) {
            this.root = root;
            if (root != null) { calculateLayout(root, 0, 0, new int[]{0}); updateMaxDimensions(root); }
            repaint();
        }
        private void updateMaxDimensions(ASTNode node) {
            if (node == null) return;
            maxX = Math.max(maxX, node.x + N_W); maxY = Math.max(maxY, node.y + N_H);
            for (ASTNode child : node.getChildren()) updateMaxDimensions(child);
        }
        private int calculateLayout(ASTNode node, int depth, int xOffset, int[] maxWidth) {
            int cWidth = 0, maxCH = 0;
            for (ASTNode child : node.getChildren()) {
                cWidth += calculateLayout(child, depth + 1, xOffset + cWidth, maxWidth) + H_S;
                maxCH = Math.max(maxCH, child.height);
            }
            if (cWidth > 0) cWidth -= H_S;
            node.width = Math.max(N_W, cWidth);
            node.height = N_H + (maxCH > 0 ? maxCH + V_S : 0);
            node.x = xOffset + Math.max(0, (cWidth - N_W) / 2);
            node.y = depth * (N_H + V_S) + 20;
            maxWidth[0] = Math.max(maxWidth[0], xOffset + node.width);
            return Math.max(N_W, cWidth);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (root != null) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawConnections(g2d, root); drawNode(g2d, root);
            }
        }
        private void drawConnections(Graphics2D g, ASTNode node) {
            for (ASTNode child : node.getChildren()) {
                g.setColor(Color.GRAY); g.drawLine(node.x + N_W/2, node.y + N_H, child.x + N_W/2, child.y);
                drawConnections(g, child);
            }
        }
        private void drawNode(Graphics2D g, ASTNode node) {
            g.setColor(new Color(173, 216, 230));
            g.fillRoundRect(node.x, node.y, N_W, N_H, 15, 15);
            g.setColor(Color.DARK_GRAY);
            g.drawRoundRect(node.x, node.y, N_W, N_H, 15, 15);
            g.setColor(Color.BLACK);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(node.label, node.x + (N_W - fm.stringWidth(node.label))/2, node.y + 25);
            for (ASTNode child : node.getChildren()) drawNode(g, child);
        }
        @Override public Dimension getPreferredSize() { return new Dimension(maxX + 100, maxY + 100); }
    }
    
    public static void main(String[] args) {
        // Asegurar diseño del sistema nativo (Evita errores visuales)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        
        SwingUtilities.invokeLater(() -> new Java().setVisible(true));
    }
}