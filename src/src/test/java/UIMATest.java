import org.apache.uima.cas.CASException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasIOUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

@Nested
public class UIMATest {

    @Test
    @DisplayName("Create CAS")
    public void testCreatingCas() throws ResourceInitializationException, CASException {

        JCas pCas = JCasFactory.createJCas();

        assert pCas!=null;
    }


    @Test
    @DisplayName("Read CAS")
    public void testReadingCas() throws ResourceInitializationException, CASException, IOException {

        JCas pCas = JCasFactory.createJCas();

        InputStream in = getClass().getClassLoader().getResourceAsStream("input/7.xmi");

        CasIOUtils.load(in, pCas.getCas());

        assert JCasUtil.selectAll(pCas).size()>10;

    }
}
