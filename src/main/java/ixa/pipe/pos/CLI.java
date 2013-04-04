/*
 * Copyright 2013 Rodrigo Agerri

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package ixa.pipe.pos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.LinkedHashMap;
import java.util.List;

import net.didion.jwnl.JWNLException;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * IXA-OpenNLP POS tagging using Apache OpenNLP. Lemmatization provided by JWNL.
 * 
 * @author ragerri
 * @version 1.0
 * 
 */

public class CLI {

  /**
   * 
   * 
   * BufferedReader (from standard input) and BufferedWriter are opened. The
   * module takes KAF and reads the header, and the text elements and uses
   * Annotate class to annotate POS tags and obtain lemmas. The terms elements
   * with POS annotation are then added to the KAF received via standard input.
   * Finally, the modified KAF document is passed via standard output.
   * 
   * @param args
   * @throws IOException
   * @throws JDOMException
   * @throws JWNLException
   */

  public static void main(String[] args) throws IOException, JDOMException,
      JWNLException {

    Namespace parsedArguments = null;

    // create Argument Parser
    ArgumentParser parser = ArgumentParsers
        .newArgumentParser("ixa-opennlp-pos-1.0.jar")
        .description(
            "ixa-opennlp-pos-1.0 is a multilingual POS tagger module developed by IXA NLP Group based on Apache OpenNLP.\n");

    // specify language
    parser
        .addArgument("-l", "--lang")
        .choices("en", "es")
        .required(true)
        .help(
            "It is REQUIRED to choose a language to perform annotation with IXA-OpenNLP");
    // parser.addArgument("-f","--format").choices("kaf","plain").setDefault("kaf").help("output annotation in plain native "
    // +
    // "Apache OpenNLP format or in KAF format. The default is KAF");

    // specify wordnet dict path
    parser
        .addArgument("-w", "--wordnet")
        .required(true)
        .help(
            "Provide path to WordNet dict directory. WordNet is used to provide lemmatization");

    /*
     * Parse the command line arguments
     */

    // catch errors and print help
    try {
      parsedArguments = parser.parseArgs(args);
    } catch (ArgumentParserException e) {
      parser.handleError(e);
      System.out
          .println("Run java -jar target/ixa-opennlp-pos-1.0.jar -help for details");
      System.exit(1);
    }

    /*
     * Load language and dictionary parameters and construct annotators, read
     * and write kaf
     */

    // String wnDirectory = args[0];
    String lang = parsedArguments.getString("lang");
    String wnDirectory = parsedArguments.getString("wordnet");
    KAFReader kafReader = new KAFReader();
    Annotate annotator = new Annotate(lang, wnDirectory);
    StringBuilder sb = new StringBuilder();
    BufferedReader breader = null;
    BufferedWriter bwriter = null;
    KAF kaf = new KAF(lang);
    try {
      breader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
      bwriter = new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8"));
      String line;
      while ((line = breader.readLine()) != null) {
        sb.append(line);
      }

      // read KAF from standard input
      InputStream kafIn = new ByteArrayInputStream(sb.toString().getBytes(
          "UTF-8"));
      Element rootNode = kafReader.getRootNode(kafIn);
      List<Element> lingProc = kafReader.getKafHeader(rootNode);
      List<Element> wfs = kafReader.getWfs(rootNode);
      LinkedHashMap<String, List<String>> sentencesMap = kafReader
          .getSentencesMap(wfs);
      LinkedHashMap<String, List<String>> sentences = kafReader
          .getSentsFromWfs(sentencesMap, wfs);

      // add already contained header plus this module linguistic
      // processor
      annotator.addKafHeader(lingProc, kaf);
      kaf.addlps("terms", "ixa-opennlp-pos-" + lang, kaf.getTimestamp(), "1.0");

      // annotate POS tags to KAF and lemmatize
      annotator.annotatePOSToKAF(sentences, kaf);

      XMLOutputter xout = new XMLOutputter(Format.getPrettyFormat());
      xout.output(kaf.createKAFDoc(), bwriter);
      bwriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}