package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.context.parser.Identifier;
import ai.vespa.schemals.context.parser.IdentifyDeprecatedToken;
import ai.vespa.schemals.context.parser.IdentifyIdentifier;
import ai.vespa.schemals.context.parser.IdentifySymbolReferences;
import ai.vespa.schemals.context.parser.IdentifyType;
import ai.vespa.schemals.parser.*;

import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

public class SchemaDocumentParser {

    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private SchemaIndex schemaIndex;

    private ArrayList<Identifier> parseIdentifiers;

    private String fileURI = "";
    private String content = "";
    
    private SchemaNode CST;
    private boolean faultySchema = true;

    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI) {
        this(logger, diagnosticsHandler, schemaIndex, fileURI, null);
    }
    
    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI, String content) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
        this.fileURI = fileURI;

        SchemaDocumentParser self = this;

        this.parseIdentifiers = new ArrayList<Identifier>() {{
            add(new IdentifyIdentifier(logger, self, schemaIndex));
            add(new IdentifyType(logger));
            add(new IdentifySymbolReferences(logger, self, schemaIndex));
            add(new IdentifyDeprecatedToken(logger));
        }};

        if (content != null) {
            this.content = content;
            parseContent();
        };
    }

    public void updateFileContent(String content) {
        this.content = content;
        parseContent();
    }

    public String getFileURI() {
        return fileURI;
    }

    public String getFileName() {
        Integer splitPos = fileURI.lastIndexOf('/');
        return fileURI.substring(splitPos + 1);
    }

    private void parseContent() {
        CharSequence sequence = content;

        logger.println("Parsing document: " + fileURI);

        ParsedBlock.setCanIgnore(true);

        SchemaParser parserStrict = new SchemaParser(getFileName(), sequence);
        parserStrict.setParserTolerant(false);

        ArrayList<Diagnostic> errors = new ArrayList<Diagnostic>();

        try {

            ParsedSchema root = parserStrict.Root();
            faultySchema = false;
        } catch (ParseException e) {
            faultySchema = true;

            Node.TerminalNode node = e.getToken();

            Range range = CSTUtils.getNodeRange(node);

            errors.add(new Diagnostic(range, e.getMessage()));
        } catch (IllegalArgumentException e) {
            // Complex error, invalidate the whole document

            errors.add(
                new Diagnostic(
                    new Range(
                        new Position(0, 0),
                        new Position((int)content.lines().count() - 1, 0)
                    ),
                    e.getMessage())
                );

            diagnosticsHandler.publishDiagnostics(fileURI, errors);
            
            return;
        }

        SchemaParser parserFaultTolerant = new SchemaParser(getFileName(), sequence);
        try {
            parserFaultTolerant.Root();
        } catch (ParseException e) {
            // Ignore
        } catch (IllegalArgumentException e) {
            // Ignore
        }

        Node node = parserFaultTolerant.rootNode();
        errors.addAll(parseCST(node));

        errors.addAll(findDirtyNode(node));

        CSTUtils.printTree(logger, CST);

        diagnosticsHandler.publishDiagnostics(fileURI, errors);

    }

    private ArrayList<Diagnostic> traverseCST(SchemaNode node) {

        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();
        
        for (Identifier identifier : parseIdentifiers) {
            ret.addAll(identifier.identify(node));
        }

        for (int i = 0; i < node.size(); i++) {
            ret.addAll(traverseCST(node.get(i)));
        }

        return ret;
    }

    private ArrayList<Diagnostic> parseCST(Node node) {
        schemaIndex.clearDocument(fileURI);
        CST = new SchemaNode(node);
        if (node == null) {
            return new ArrayList<Diagnostic>();
        }
        return traverseCST(CST);
    }

    private ArrayList<Diagnostic> findDirtyNode(Node node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        for (Node child : node) {
            ret.addAll(findDirtyNode(child));
        }

        if (node.isDirty() && ret.size() == 0) {
            Range range = CSTUtils.getNodeRange(node);

            ret.add(new Diagnostic(range, "Dirty Node"));
        }


        return ret;
    }

    public boolean isFaulty() {
        return faultySchema;
    }

    public SchemaNode getRootNode() {
        return CST;
    }

    private SchemaNode getNodeAtPositon(SchemaNode node, Position pos, boolean onlyLeaf) {
        if (node.isLeaf() && CSTUtils.positionInRange(node.getRange(), pos)) {
            return node;
        }

        Integer lowerLimit = 0;
        Integer upperLimit = node.size();

        Integer currentSearch = (upperLimit + lowerLimit) / 2;

        while (lowerLimit <= upperLimit) {
            SchemaNode search = node.get(currentSearch);

            if (CSTUtils.positionLT(pos, search.getRange().getEnd())) {

                if (CSTUtils.positionInRange(search.getRange(), pos)) {
                    return getNodeAtPositon(search, pos, onlyLeaf);
                }

                upperLimit = currentSearch - 1;
            } else {
                lowerLimit = currentSearch + 1;
            }

            currentSearch = (upperLimit + lowerLimit) / 2;
        }

        if (CSTUtils.positionInRange(node.getRange(), pos) && !onlyLeaf) {
            return node;
        }

        return null;
    }

    public SchemaNode getLeafNodeAtPosition(Position pos) {
        return getNodeAtPositon(CST, pos, true);
    }

    public SchemaNode getNodeAtPosition(Position pos) {
        return getNodeAtPositon(CST, pos, false);
    }

}
