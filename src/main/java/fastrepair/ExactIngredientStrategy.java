package fastrepair;

import fastrepair.yousei.propose.StatementRecommender;
import fr.inria.astor.approaches.jgenprog.operators.ReplaceOp;
import fr.inria.astor.core.entities.Ingredient;
import fr.inria.astor.core.entities.ModificationPoint;
import fr.inria.astor.core.loop.spaces.ingredients.IngredientSpace;
import fr.inria.astor.core.loop.spaces.ingredients.ingredientSearch.UniformRandomIngredientSearch;
import fr.inria.astor.core.loop.spaces.ingredients.scopes.IngredientSpaceScope;
import fr.inria.astor.core.loop.spaces.operators.AstorOperator;
import fr.inria.astor.core.manipulation.sourcecode.VariableResolver;
import fr.inria.astor.core.setup.ConfigurationProperties;
import org.apache.log4j.Logger;
import spoon.reflect.declaration.CtElement;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by s-sumi on 16/08/01.
 */
public class ExactIngredientStrategy extends UniformEfficientIngredientSearch {

    public ExactIngredientStrategy(IngredientSpace space) throws Exception{
        super(space);
    }

    protected Logger log = Logger.getLogger(this.getClass().getName());

    /**
     * Ingredients already selected
     */
    protected Map<String, List<String>> appliedCache = new HashMap<String, List<String>>();



    /**
     * Return an ingredient. As it has a cache, it never returns twice the same
     * ingredient.
     *
     * @param modificationPoint
     * @param operationType
     * @return
     */
    @Override
    public Ingredient getFixIngredient(ModificationPoint modificationPoint, AstorOperator operationType) {

        int attempts = 0;

        boolean continueSearching = true;

        int elementsFromFixSpace = getSpaceSize(modificationPoint, operationType);

        while (continueSearching && attempts < elementsFromFixSpace) {  //取り出した回数がFixSpaceのサイズが上回ってないかみてるので、取り出すIndexが被ってはいけない。
            Ingredient exactIngredient = super.getFixIngredient(modificationPoint, operationType);

            if (exactIngredient == null || exactIngredient.getCode() == null) {
                return null;
            }
            CtElement elementFromIngredient = exactIngredient.getCode();

            attempts++;

            boolean alreadyApplied = alreadySelected(modificationPoint, elementFromIngredient, operationType);

            if (!alreadyApplied && !elementFromIngredient.getSignature()
                    .equals(modificationPoint.getCodeElement().getSignature())) {

                continueSearching = !VariableResolver.fitInPlace(modificationPoint.getContextOfModificationPoint(),
                        elementFromIngredient);

            }

            if (!continueSearching) {
                IngredientSpaceScope scope = determineIngredientScope(modificationPoint.getCodeElement(),
                        elementFromIngredient);

                return new Ingredient(elementFromIngredient, scope);
            }

        }

        log.debug("--- no mutation left to apply in element " + modificationPoint.getCodeElement().getSignature());
        return null;

    }

    /**
     * Return the number of ingredients according to: the location and the
     * operator to apply.
     *
     * @param modificationPoint
     * @param operationType
     * @return
     */
    protected int getSpaceSize(ModificationPoint modificationPoint, AstorOperator operationType) {

        String type = null;

        if (operationType instanceof ReplaceOp) {
            type = modificationPoint.getCodeElement().getClass().getSimpleName();
        }

        List<?> allIng = null;
        if (type == null) {
            allIng = this.ingredientSpace.getIngredients(modificationPoint.getCodeElement());
        } else {
            allIng = this.ingredientSpace.getIngredients(modificationPoint.getCodeElement(), type);
        }

        if (allIng == null || allIng.isEmpty()) {
            return 0;
        }

        return allIng.size();
    }

    @Deprecated
    protected IngredientSpaceScope determineIngredientScope(CtElement modificationpoint, CtElement selectedFix,
                                                            List<?> ingredients) {
        // This is the original ingredient scope
        IngredientSpaceScope orig = determineIngredientScope(modificationpoint, selectedFix);

        String fixStr = selectedFix.toString();

        // Now, we search for equivalent fixes with different scopes
        for (Object ing : ingredients) {
            try {
                ing.toString();
            } catch (Exception e) {
                // if we cannot print the ingredient, we return
                log.error(e.toString());
                continue;
            }
            // if it's the same fix
            if (ing.toString().equals(fixStr)) {
                IngredientSpaceScope n = determineIngredientScope(modificationpoint, (CtElement) ing);
                // if the scope of the ingredient ing is narrower than the fix,
                // we keep it.
                if (n.ordinal() < orig.ordinal()) {
                    orig = n;
                    // if it's local, we return
                    if (IngredientSpaceScope.values()[0].equals(orig))
                        return orig;
                }

            }
        }
        return orig;
    }

    protected IngredientSpaceScope determineIngredientScope(CtElement ingredient, CtElement fix) {

        File ingp = ingredient.getPosition().getFile();
        File fixp = fix.getPosition().getFile();

        if (ingp.getAbsolutePath().equals(fixp.getAbsolutePath())) {
            return IngredientSpaceScope.LOCAL;
        }
        if (ingp.getParentFile().getAbsolutePath().equals(fixp.getParentFile().getAbsolutePath())) {
            return IngredientSpaceScope.PACKAGE;
        }
        return IngredientSpaceScope.GLOBAL;
    }

    /**
     * Check if the ingredient was already used
     * @return
     */
    protected boolean alreadySelected(ModificationPoint gen, CtElement fixElement, AstorOperator operator) {
        // we add the instance identifier to the patch.
        String lockey = gen.getCodeElement() + "-" + operator.toString();
        String fix = "";
        try {
            fix = fixElement.toString();
        } catch (Exception e) {
            log.error("to string fails");
        }
        List<String> prev = appliedCache.get(lockey);
        // The element does not have any mutation applied
        if (prev == null) {
            prev = new ArrayList<String>();
            prev.add(fix);
            appliedCache.put(lockey, prev);
            return false;
        } else {
            // The element has mutation applied
            if (prev.contains(fix))
                return true;
            else {
                prev.add(fix);
                return false;
            }
        }
    }
}
