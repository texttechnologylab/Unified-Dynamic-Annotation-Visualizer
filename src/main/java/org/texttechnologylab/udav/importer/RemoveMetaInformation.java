package org.texttechnologylab.udav.importer;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.impl.FeatureStructureImplC;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.texttechnologylab.annotation.SpacyAnnotatorMetaData;

import java.util.HashSet;
import java.util.Set;

public class RemoveMetaInformation extends JCasAnnotator_ImplBase {

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {

        Set<TOP> acRemove = new HashSet<>(JCasUtil.select(jCas, SpacyAnnotatorMetaData.class));

        acRemove.forEach(FeatureStructureImplC::removeFromIndexes);

    }
}
