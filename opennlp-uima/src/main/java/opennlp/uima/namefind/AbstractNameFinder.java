/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.uima.namefind;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.util.Span;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.ContainingConstraint;
import opennlp.uima.util.UimaUtil;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

abstract class AbstractNameFinder extends CasAnnotator_ImplBase {

  protected final String name;
  
  protected Type mSentenceType;

  protected Type mTokenType;

  protected Type mNameType;
  
  protected UimaContext context;
  
  protected Logger mLogger;
  
  private Boolean isRemoveExistingAnnotations;
  
  AbstractNameFinder(String name) {
    this.name = name;
  }
  
  protected void initialize() throws ResourceInitializationException {
  }
  
  public final void initialize(UimaContext context) throws ResourceInitializationException {
    
	this.context = context;
	  
    mLogger = context.getLogger();
    
    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, 
      "Initializing the " + name + ".");
    } 
    
    isRemoveExistingAnnotations = AnnotatorUtil.getOptionalBooleanParameter(
        context, UimaUtil.IS_REMOVE_EXISTINGS_ANNOTAIONS);

    if (isRemoveExistingAnnotations == null) {
        isRemoveExistingAnnotations = false;
    }
    
    initialize();
  }
  
  /**
   * Initializes the type system.
   */
  public void typeSystemInit(TypeSystem typeSystem)
      throws AnalysisEngineProcessException {

    // sentence type
	  mSentenceType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        UimaUtil.SENTENCE_TYPE_PARAMETER);

    // token type
    mTokenType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        UimaUtil.TOKEN_TYPE_PARAMETER);

    // name type
    mNameType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        NameFinder.NAME_TYPE_PARAMETER);
  }
  
  protected void postProcessAnnotations(Span detectedNames[], 
		  AnnotationFS[] nameAnnotations) {
  }
  
  /**
   * Called if the current document is completely processed. 
   */
  protected void documentDone(CAS cas) {
  }
  
  protected abstract Span[] find(CAS cas, AnnotationFS sentence, List<AnnotationFS> tokenAnnotations, 
      String[] tokens);
  
  /**
   * Performs name finding on the given cas object.
   */
  public final void process(CAS cas) {

    FSIndex<AnnotationFS> sentenceIndex = cas.getAnnotationIndex(mSentenceType);

    for (Iterator<AnnotationFS> sentenceIterator = sentenceIndex.iterator(); 
        sentenceIterator.hasNext();) {
      AnnotationFS sentenceAnnotation = (AnnotationFS) sentenceIterator.next();
      
      if (isRemoveExistingAnnotations)
        UimaUtil.removeAnnotations(cas, sentenceAnnotation, mNameType);
      
      ContainingConstraint containingConstraint =
          new ContainingConstraint(sentenceAnnotation);

      Iterator<AnnotationFS> tokenIterator = cas.createFilteredIterator(
          cas.getAnnotationIndex(mTokenType).iterator(), containingConstraint);
      
      List<AnnotationFS> tokenAnnotationList = new ArrayList<AnnotationFS>();
      
      List<String> tokenList = new ArrayList<String>();

      while (tokenIterator.hasNext()) {
        
        AnnotationFS tokenAnnotation = (AnnotationFS) tokenIterator
            .next();

        tokenAnnotationList.add(tokenAnnotation);
        
        tokenList.add(tokenAnnotation.getCoveredText());
      }
       
      Span[] names  = find(cas, sentenceAnnotation, tokenAnnotationList, 
          (String[]) tokenList.toArray(new String[tokenList.size()]));
      
      // TODO: log names, maybe with NameSample class
      
      AnnotationFS nameAnnotations[] = new AnnotationFS[names.length];
      
      for (int i = 0; i < names.length; i++) {
        
        int startIndex = ((AnnotationFS) tokenAnnotationList.get(
            names[i].getStart())).getBegin();

        int endIndex = ((AnnotationFS) tokenAnnotationList.get(
            names[i].getEnd() - 1)).getEnd();
        
        nameAnnotations[i] = 
            cas.createAnnotation(mNameType, startIndex, endIndex);
        
        cas.getIndexRepository().addFS(nameAnnotations[i]);
      }
      
      postProcessAnnotations(names, nameAnnotations);
    }
    
    documentDone(cas);
  }
}