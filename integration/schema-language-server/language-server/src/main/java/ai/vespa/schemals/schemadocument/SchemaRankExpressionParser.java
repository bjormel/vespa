package ai.vespa.schemals.schemadocument;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.rankingexpression.RankingExpressionParser;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;
import ai.vespa.schemals.tree.rankingexpression.RankingExpressionUtils;

public class SchemaRankExpressionParser {

    private static final HashSet<TokenType> multilineTokens = new HashSet<>() {{
        add(TokenType.EXPRESSION_ML);
        add(TokenType.SUMMARYFEATURES_ML);
        add(TokenType.SUMMARYFEATURES_ML_INHERITS);
        add(TokenType.MATCHFEATURES_ML);
        add(TokenType.MATCHFEATURES_ML_INHERITS);
        add(TokenType.RANKFEATURES_ML);
    }};

    private static final HashSet<TokenType> inheritsTokens = new HashSet<>() {{
        add(TokenType.SUMMARYFEATURES_ML_INHERITS);
        add(TokenType.MATCHFEATURES_ML_INHERITS);
    }};

    // This has to match the tokens in the ccc file
    private static final HashMap<TokenType, String> preTextMap = new HashMap<TokenType, String>() {{
        put(TokenType.EXPRESSION_SL, "expression");
        put(TokenType.EXPRESSION_ML, "expression");
        put(TokenType.SUMMARYFEATURES_SL, "summary-features");
        put(TokenType.SUMMARYFEATURES_ML, "summary-features");
        put(TokenType.SUMMARYFEATURES_ML_INHERITS, "summary-features inherits");
        put(TokenType.MATCHFEATURES_SL, "match-features");
        put(TokenType.MATCHFEATURES_ML, "match-features");
        put(TokenType.MATCHFEATURES_ML_INHERITS, "match-features inherits");
        put(TokenType.RANKFEATURES_SL, "rank-features");
        put(TokenType.RANKFEATURES_ML, "rank-features");
    }};

    private static final HashMap<TokenType, TokenType> simplifyTokenTypeMap = new HashMap<TokenType, TokenType>() {{
        put(TokenType.EXPRESSION_SL, TokenType.EXPRESSION_SL);
        put(TokenType.EXPRESSION_ML, TokenType.EXPRESSION_SL);
        put(TokenType.SUMMARYFEATURES_SL, TokenType.SUMMARYFEATURES_SL);
        put(TokenType.SUMMARYFEATURES_ML, TokenType.SUMMARYFEATURES_SL);
        put(TokenType.SUMMARYFEATURES_ML_INHERITS, TokenType.SUMMARYFEATURES_SL);
        put(TokenType.MATCHFEATURES_SL, TokenType.MATCHFEATURES_SL);
        put(TokenType.MATCHFEATURES_ML, TokenType.MATCHFEATURES_SL);
        put(TokenType.MATCHFEATURES_ML_INHERITS, TokenType.MATCHFEATURES_SL);
        put(TokenType.RANKFEATURES_SL, TokenType.RANKFEATURES_SL);
        put(TokenType.RANKFEATURES_ML, TokenType.RANKFEATURES_SL);
    }};

    private static record ExpressionMetaData(
        boolean mulitline,
        boolean inherits,
        Position expressionOffset,
        String preText
    ) {}

    private static ExpressionMetaData findExpressionMetaData(SchemaNode node) {

        TokenType nodeType = node.findFirstLeaf().getSchemaType();

        boolean inherits = inheritsTokens.contains(nodeType);

        boolean isMultiline = multilineTokens.contains(nodeType);

        char splitChar = isMultiline ? '{' : ':';

        int offset = node.getText().indexOf(splitChar) + 1;

        String preString = node.getText().substring(0, offset);

        long numberOfNewLines = preString.chars().filter(ch -> ch == '\n').count();

        if (numberOfNewLines > 0) {
            offset = preString.length() - preString.lastIndexOf('\n');
        }

        Position expressionOfffset = new Position(
            (int)numberOfNewLines,
            offset
        );

        return new ExpressionMetaData(
            isMultiline,
            inherits,
            expressionOfffset,
            preString
        );
    }

    static void printExpressionTree(PrintStream logger, ExpressionNode node, int indent) {
        String[] classList = node.getClass().toString().split("[.]");
        String className = classList[classList.length - 1];
        String msg = node.toString() + " (" + className + ")";

        if (node instanceof ReferenceNode) {
            var ref = ((ReferenceNode)node).reference();

            msg += " [REF: " + ref.name() + "]";

            if (ref.isIdentifier()) {
                msg += " [IDENTIFIER]";
            } 
            if (ref.output() != null) {
                msg += " [OUTPUT: " + ref.output() + "]";
            }
        }
        logger.println(new String(new char[indent]).replaceAll("\0", "\t") + msg);
        if (node instanceof CompositeNode) {
            for (var child : ((CompositeNode)node).children()) {
                printExpressionTree(logger, child, indent + 1);
            }
        }
    }

    static SchemaNode parseRankingExpression(ParseContext context, SchemaNode node, Position expressionOffset, ArrayList<Diagnostic> diagnostics) {
        String expressionString = node.getRankExpressionString();

        Position expressionStart = CSTUtils.addPositions(node.getRange().getStart(), expressionOffset);
        
        if (expressionString == null)return null;
        CharSequence sequence = expressionString;

        RankingExpressionParser parser = new RankingExpressionParser(context.logger(), context.fileURI(), sequence);
        parser.setParserTolerant(false);

        List<ExpressionNode> nodes = new ArrayList<>();

        try {
            if (node.containsExpressionData()) {
                nodes.add(parser.expression());
            } else {
                nodes.addAll(parser.featureList());
            }


            for (var enode : nodes) {
                context.logger().println("FOUND NODE:");
                printExpressionTree(context.logger(), enode, 1);
            }

            return new SchemaNode(parser.rootNode(), expressionStart);
        } catch(ai.vespa.schemals.parser.rankingexpression.ParseException pe) {

            Range range = RankingExpressionUtils.getNodeRange(pe.getToken());
            
            range = CSTUtils.addPositionToRange(expressionStart, range);

            diagnostics.add(new Diagnostic(range, pe.getMessage()));

        } catch(IllegalArgumentException ex) {
            // TODO: test this
            diagnostics.add(new Diagnostic(node.getRange(), ex.getMessage(), DiagnosticSeverity.Error, ""));
        }

        RankingExpressionParser tolerantParser = new RankingExpressionParser(context.logger(), context.fileURI(), sequence);
        tolerantParser.setParserTolerant(true);

        try {
            if (node.containsExpressionData()) {
                tolerantParser.expression();
            } else {
                tolerantParser.featureList();
            }

            return new SchemaNode(tolerantParser.rootNode(), expressionStart);
        } catch (ai.vespa.schemals.parser.rankingexpression.ParseException pe) {
            // Ignore
        } catch (IllegalArgumentException ex) {
            // Ignore
        }

        return null;
    }

    private static SchemaNode tokenFromRawText(ParseContext context, SchemaNode node, TokenType type, String text, int start, int end) {

        Position startPos = SchemaDocument.PositionAddOffset(context.content(), node.getRange().getStart(), start);
        Position endPos = SchemaDocument.PositionAddOffset(context.content(), node.getRange().getStart(), end);

        Range range = new Range(startPos, endPos);
        
        SchemaNode ret = new SchemaNode(
            range,
            text,
            type.toString().toUpperCase() + " [CUSTOM LANGUAGE]"
        );

        ret.setSchemaType(type);

        return ret;
    }

    private static final HashSet<Character> skipCharacters = new HashSet<>() {{
        add(' ');
        add('\t');
        add('\r');
        add('\f');
    }};

    private static SchemaNode parseIdentifierWithDash(ParseContext context, SchemaNode node, String text, int startSearch, int endSearch) {
        context.logger().println(text);
        context.logger().println(text.length());
        context.logger().println(startSearch);
        context.logger().println(endSearch);
        String subString = text.substring(startSearch, endSearch);
        int leadingSplitPos = 0;
        while (
            skipCharacters.contains(subString.charAt(leadingSplitPos)) &&
            leadingSplitPos < subString.length() - 1
        ) {
            leadingSplitPos++;
        }

        int trailingSplitPos = subString.length() - 1;
        while (
            skipCharacters.contains(subString.charAt(trailingSplitPos)) &&
            trailingSplitPos > 0
        ) {
            trailingSplitPos--;
        }

        Position subStringBeginPos = SchemaDocument.PositionAddOffset(context.content(), node.getRange().getStart(), startSearch);

        Range range = new Range(
            SchemaDocument.PositionAddOffset(context.content(), subStringBeginPos, leadingSplitPos),
            SchemaDocument.PositionAddOffset(context.content(), subStringBeginPos, trailingSplitPos)
        );

        String identifierString = TokenType.IDENTIFIER_WITH_DASH.toString().toUpperCase() + " [CUSTOM LANGUAGE]";

        SchemaNode child = new SchemaNode(range, subString, identifierString);
        SchemaNode parent = new SchemaNode(range, subString, "identifierWithDashStr [CUSTOM LANGUAGE]");

        parent.addChild(child);
        parent.setSimulatedASTClass(identifierWithDashStr.class);
        return parent;
    }

    private static ArrayList<SchemaNode> findPreChildren(ParseContext context, ExpressionMetaData metaData, SchemaNode node) {
        ArrayList<SchemaNode> children = new ArrayList<>();

        TokenType nodeType = node.findFirstLeaf().getSchemaType();
        String firstTokenString = preTextMap.get(nodeType);
        if (firstTokenString == null) {
            return null;
        }


        TokenType simplifiedType = simplifyTokenTypeMap.get(nodeType);
        if (simplifiedType == null) return null;

        // Find the last token
        char searchChar   = metaData.mulitline() ? '{' : ':';
        TokenType charTokenType = metaData.mulitline() ? TokenType.LBRACE : TokenType.COLON;
        int charPosition = metaData.preText().indexOf(searchChar, firstTokenString.length());

        if (!metaData.inherits()) {
            children.add(tokenFromRawText(context, node, simplifiedType, firstTokenString, 0, firstTokenString.length()));

        } else {
            int spacePos = firstTokenString.indexOf(' ');
            children.add(tokenFromRawText(
                context,
                node,
                simplifiedType,
                firstTokenString.substring(0, spacePos),
                0,
                spacePos
            ));
            children.add(tokenFromRawText(
                context,
                node,
                TokenType.INHERITS,
                firstTokenString.substring(spacePos + 1, firstTokenString.length()),
                spacePos + 1,
                firstTokenString.length()
            ));

            // We know at between the inherits and the charPosition it will be an IdentifierWithDashStr
            children.add(parseIdentifierWithDash(context, node, metaData.preText(), firstTokenString.length(), charPosition));
        }

        children.add(tokenFromRawText(context, node, charTokenType, "" + searchChar, charPosition, charPosition + 1));

        return children;
    }

    static void embedCST(ParseContext context, SchemaNode node, ArrayList<Diagnostic> diagnostics) {
        if (!node.containsOtherLanguageData(LanguageType.RANK_EXPRESSION)) return;


        ExpressionMetaData metaData = findExpressionMetaData(node);

        ArrayList<SchemaNode> newChildren = findPreChildren(context, metaData, node);
        if (newChildren == null) return;

        SchemaNode rankExpressionNode = parseRankingExpression(context, node, metaData.expressionOffset(), diagnostics);

        if (rankExpressionNode != null) {
            newChildren.add(rankExpressionNode);
        }

        if (metaData.mulitline()) {
            newChildren.add(tokenFromRawText(context, node, TokenType.RBRACE, "}", node.getText().length() - 1, node.getText().length()));
        }

        node.clearChildren();
        node.addChildren(newChildren);
    }
}
