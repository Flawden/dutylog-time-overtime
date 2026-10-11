package ru.daniil.shifts.web;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.service.exception.ApiException;
import static org.junit.jupiter.api.Assertions.*;
class Article153ReviewInputTest {
    @Test void rejectsUnknownFieldsMissingFieldsCoercedNumbersAndDuplicateKeys() {
        for(String value : new String[] {"{}", "{\"configurationToken\":\"x\",\"expectedRevision\":\"0\",\"localSource\":null,\"components\":[],\"review\":null}", "{\"configurationToken\":\"x\",\"expectedRevision\":0.5,\"localSource\":null,\"components\":[],\"review\":null}", "{\"configurationToken\":\"x\",\"expectedRevision\":0,\"localSource\":null,\"components\":[],\"review\":null,\"ownerId\":5}", "{\"configurationToken\":\"x\",\"configurationToken\":\"y\",\"expectedRevision\":0,\"localSource\":null,\"components\":[],\"review\":null}"})
            assertThrows(ApiException.class,()->Article153ReviewController.decode(value));
    }
}
