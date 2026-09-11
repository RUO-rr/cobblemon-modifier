package com.cobblemon.modifier.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class FormInfoTest {

    // ================================================================
    // extractFromJson
    // ================================================================

    @Test
    public void extractFromJson_shouldReturnEmptyListForNoForms() {
        JsonArray empty = new JsonArray();
        List<FormInfo> forms = FormInfo.extractFromJson(empty);
        assertNotNull(forms);
        assertTrue(forms.isEmpty());
    }

    @Test
    public void extractFromJson_shouldExtractMegaX() {
        JsonArray formsArray = new JsonArray();
        JsonObject megaX = new JsonObject();
        megaX.addProperty("name", "Mega-X");
        megaX.addProperty("requiredMove", "close-combat");
        formsArray.add(megaX);

        List<FormInfo> forms = FormInfo.extractFromJson(formsArray);

        assertEquals(1, forms.size());
        FormInfo form = forms.get(0);
        assertEquals("Mega-X", form.name());
        assertEquals("Mega-X", form.displayName());
        assertEquals("_mega_x", form.fieldSuffix());
    }

    @Test
    public void extractFromJson_shouldExtractMegaY() {
        JsonArray formsArray = new JsonArray();
        JsonObject megaY = new JsonObject();
        megaY.addProperty("name", "Mega-Y");
        formsArray.add(megaY);

        List<FormInfo> forms = FormInfo.extractFromJson(formsArray);

        assertEquals(1, forms.size());
        assertEquals("_mega_y", forms.get(0).fieldSuffix());
    }

    @Test
    public void extractFromJson_shouldExtractGenericMega() {
        JsonArray formsArray = new JsonArray();
        JsonObject mega = new JsonObject();
        mega.addProperty("name", "Mega-Steel");
        formsArray.add(mega);

        List<FormInfo> forms = FormInfo.extractFromJson(formsArray);

        assertEquals(1, forms.size());
        assertEquals("Mega-Steel", forms.get(0).displayName());
        assertEquals("_mega_0", forms.get(0).fieldSuffix());
    }

    @Test
    public void extractFromJson_shouldExtractRegularForm() {
        JsonArray formsArray = new JsonArray();
        JsonObject form = new JsonObject();
        form.addProperty("name", "Alola");
        formsArray.add(form);

        List<FormInfo> forms = FormInfo.extractFromJson(formsArray);

        assertEquals(1, forms.size());
        assertEquals("Alola", forms.get(0).name());
        assertEquals("Alola", forms.get(0).displayName());
        assertEquals("_form_0", forms.get(0).fieldSuffix());
    }

    @Test
    public void extractFromJson_shouldExtractMultipleForms() {
        JsonArray formsArray = new JsonArray();

        JsonObject base = new JsonObject();
        base.addProperty("name", "Base-Form");
        formsArray.add(base);

        JsonObject megaX = new JsonObject();
        megaX.addProperty("name", "Mega-X");
        formsArray.add(megaX);

        JsonObject alola = new JsonObject();
        alola.addProperty("name", "Alola");
        formsArray.add(alola);

        List<FormInfo> forms = FormInfo.extractFromJson(formsArray);

        assertEquals(3, forms.size());
        assertEquals("_form_0", forms.get(0).fieldSuffix());
        assertEquals("_mega_x", forms.get(1).fieldSuffix());
        assertEquals("_form_2", forms.get(2).fieldSuffix());  // index = 2
    }

    @Test
    public void extractFromJson_shouldHandleMegaXYSequence() {
        JsonArray formsArray = new JsonArray();
        JsonObject megaX = new JsonObject();
        megaX.addProperty("name", "Mega-X");
        formsArray.add(megaX);
        JsonObject megaY = new JsonObject();
        megaY.addProperty("name", "Mega-Y");
        formsArray.add(megaY);

        List<FormInfo> forms = FormInfo.extractFromJson(formsArray);

        assertEquals(2, forms.size());
        assertEquals("_mega_x", forms.get(0).fieldSuffix());
        assertEquals("_mega_y", forms.get(1).fieldSuffix());
    }

    @Test
    public void extractFromJson_shouldSkipNonObjectElements() {
        JsonArray formsArray = new JsonArray();
        formsArray.add("not an object");  // JsonPrimitive

        JsonObject validForm = new JsonObject();
        validForm.addProperty("name", "Valid");
        formsArray.add(validForm);

        List<FormInfo> forms = FormInfo.extractFromJson(formsArray);

        assertEquals(1, forms.size());
        assertEquals("Valid", forms.get(0).name());
    }

    @Test
    public void extractFromJson_shouldSkipFormsWithoutName() {
        JsonArray formsArray = new JsonArray();
        JsonObject noName = new JsonObject();
        noName.addProperty("baseStats", "something");
        formsArray.add(noName);

        JsonObject withName = new JsonObject();
        withName.addProperty("name", "Named");
        formsArray.add(withName);

        List<FormInfo> forms = FormInfo.extractFromJson(formsArray);

        assertEquals(1, forms.size());
        assertEquals("Named", forms.get(0).name());
    }

    @Test
    public void extractFromJson_shouldSkipEmptyName() {
        JsonArray formsArray = new JsonArray();
        JsonObject emptyName = new JsonObject();
        emptyName.addProperty("name", "   ");
        formsArray.add(emptyName);

        List<FormInfo> forms = FormInfo.extractFromJson(formsArray);
        assertTrue(forms.isEmpty());
    }

    // ================================================================
    // Constructor validation
    // ================================================================

    @Test(expected = NullPointerException.class)
    public void constructor_shouldRejectNullName() {
        new FormInfo(null, "display", "_suffix");
    }

    @Test(expected = NullPointerException.class)
    public void constructor_shouldRejectNullDisplayName() {
        new FormInfo("name", null, "_suffix");
    }

    @Test(expected = NullPointerException.class)
    public void constructor_shouldRejectNullSuffix() {
        new FormInfo("name", "display", null);
    }

    // ================================================================
    // equals / hashCode
    // ================================================================

    @Test
    public void equals_shouldReturnTrueForSameValues() {
        FormInfo a = new FormInfo("Mega-X", "Mega X", "_mega_x");
        FormInfo b = new FormInfo("Mega-X", "Mega X", "_mega_x");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equals_shouldReturnFalseForDifferentSuffix() {
        FormInfo a = new FormInfo("form", "form", "_a");
        FormInfo b = new FormInfo("form", "form", "_b");
        assertNotEquals(a, b);
    }

    // ================================================================
    // toString
    // ================================================================

    @Test
    public void toString_shouldContainNameAndSuffix() {
        FormInfo form = new FormInfo("Mega-X", "Mega X", "_mega_x");
        String str = form.toString();
        assertTrue(str.contains("Mega-X"));
        assertTrue(str.contains("_mega_x"));
    }
}
