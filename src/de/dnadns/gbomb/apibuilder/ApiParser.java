package de.dnadns.gbomb.apibuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class ApiParser {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

		String outputDir = "gbapi";

		if (args.length >0 && args[0] !=null)
			outputDir = args[0];
		
		try {
			URL u = new URL("http://api.giantbomb.com/documentation/");
			ByteArrayOutputStream fos2 = crossTheStreams(u);

			@SuppressWarnings("unused")
			ApiBuilder builder = new ApiBuilder(new ByteArrayInputStream(fos2.toByteArray()), outputDir);

		} catch (Exception ioe) {
			System.err.println("I/O Error - " + ioe);
		}

	}

	// this method does some on the fly search/replace to correct the online
	// document (it's not well-formed)
	@SuppressWarnings("deprecation")
	public static ByteArrayOutputStream crossTheStreams(URL u) throws IOException {
		String str;
		DataInputStream input = new DataInputStream(u.openStream());
		ByteArrayOutputStream fos2 = new ByteArrayOutputStream();
		DataOutputStream output = new DataOutputStream(fos2);

		while (null != ((str = input.readLine()))) {

			// find
			String s2 = "<link rel=\"stylesheet\" href=\"http://media.giantbomb.com/media/api/css/api_10004.css\">";
			// replace
			String s3 = "";

			int x = 0;
			int y = 0;
			String result = "";
			while ((x = str.indexOf(s2, y)) > -1) {
				result += str.substring(y, x);
				result += s3;
				y = x + s2.length();
			}
			result += str.substring(y);
			str = result;

			if (str.indexOf("'',") != -1) {
				continue;
			} else {
				str = str + "\n";

				output.writeBytes(str);
			}
		}
		return fos2;
	}

	static class ApiBuilder {
		private InputStream in;
		private LinkedList<GBClass> classes;
		private String outputDir;

		public InputStream getIn() {
			return in;
		}

		public void setIn(InputStream in) {
			this.in = in;
		}

		public String getOutputDir() {
			return outputDir;
		}

		public void setOutputDir(String outputDir) {
			this.outputDir = outputDir;
		}

		public LinkedList<GBClass> getClasses() {
			return classes;
		}

		private class GBClass {
			private String name = null;
			private HashMap<String, String> fields = new HashMap<String, String>();

			public HashMap<String, String> getFields() {
				return fields;
			}

			public GBClass(String name) {
				this.setName(name);
				this.fields.put("api_key", "An alpha-numeric string used to identify the user making the request.");
				this.fields.put("format", "May be xml, json or jsonp.");
				this.fields.put("json_callback", "Callback parameter for the JSONP format.");
			}

			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}

			@Override
			public String toString() {
				return "gbClass [name=" + name + ", fields=" + fields + "]";
			}

		}

		public ApiBuilder(InputStream in, String outputDir) {
			super();
			this.in = in;
			this.outputDir = outputDir;
			this.classes = new LinkedList<ApiBuilder.GBClass>();
			readApiDoc();
			if (!classes.isEmpty())
				createClasses(outputDir);
		}

		private void createClasses(String outputDir2) {

			StringBuffer buf = new StringBuffer();

			boolean success = (new File(outputDir2)).mkdir();
			if (success) {
				System.out.println("Directory: " + outputDir2 + " created");
			}

			try {
				FileWriter outFile = new FileWriter(outputDir2 + "/GBApi.java");
				buf.append("package " + outputDir2 + ";\n\n");
				buf.append("public class GBApi {\n\n");
				for (GBClass current : classes) {
					String class_firstLetter = current.getName().substring(0, 1);
					String class_remainder = current.getName().substring(1);
					String class_capitalized = class_firstLetter.toUpperCase() + class_remainder.toLowerCase();

					StringBuffer bufToString = new StringBuffer();
					buf.append("public class " + class_capitalized + " {\n\n");
					bufToString.append(" + (\"\"");
					Set<Entry<String, String>> fieldSet = current.getFields().entrySet();
					for (Entry<String, String> field : fieldSet) {
						// create the field
						String fieldName = field.getKey();
						String description = field.getValue();
						// fieldName = field.getKey().replaceAll("=", "");
						buf.append("private String " + fieldName + ";\n");

						// getter/setter
						String firstLetter = fieldName.substring(0, 1);
						String remainder = fieldName.substring(1);
						String capitalized = firstLetter.toUpperCase() + remainder.toLowerCase();

						buf.append("public String get" + capitalized + "(){\n");
						buf.append("\tif (this." + fieldName + " != null){\n");

						buf.append("\t\treturn \"&" + fieldName + "=\"+ this." + fieldName + ";\n");
						buf.append("\t} else {\n");
						buf.append("\t\treturn \"\";\n");
						buf.append("\t}\n");
						buf.append("}\n\n");

						buf.append("/**\n");
						buf.append(" * @param " + fieldName + " "+ description+"\n");
						buf.append(" */\n");
						
						buf.append("public void set" + capitalized + "(String " + fieldName + "){\n");
						buf.append("this." + fieldName + " = " + fieldName + ";\n");
						buf.append("}\n\n");

						bufToString.append(" + this.get" + capitalized + "()");
					}
					bufToString.append(" ).replaceFirst(\"&\", \"\")");
					buf.append("public String toUrl(){\n");
					buf.append("return \"/" + current.getName() + "/?\" " + bufToString.toString() + ";\n");
					buf.append("}\n\n");

					buf.append("}\n");

				}
				buf.append("}");
				PrintWriter out = new PrintWriter(outFile);

				out.write(buf.toString());
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		private void readApiDoc() {
			try {
				// First create a new XMLInputFactory
				XMLInputFactory inputFactory = XMLInputFactory.newInstance();
				inputFactory.setProperty(XMLInputFactory.IS_VALIDATING, false);

				// Setup a new eventReader

				XMLStreamReader eventReader = inputFactory.createXMLStreamReader(in);

				boolean resourceContentEntered = false;
				boolean resourceEntered = false;
				boolean filtersEntered = false;
				boolean fieldEntered = false;
				boolean descriptionEntered = false;
				GBClass currentClass = null;
				String currentField = null;
				String currentDescription = "";
				Pattern resourcePattern = Pattern.compile("^Resource: /([^/]+)/");

				while (eventReader.hasNext()) {
					int event = eventReader.next();

					// content entered
					if (!resourceContentEntered && event == XMLStreamConstants.START_ELEMENT) {
						if (eventReader.getLocalName().equals("div")) {
							for (int i = 0; i < eventReader.getAttributeCount(); i++) {
								if (eventReader.getAttributeName(i).getLocalPart().equals("class")
										&& eventReader.getAttributeValue(i).equals("resources")) {
									resourceContentEntered = true;
								}
							}
						}
					}

					// find the start of a new resource type
					if (resourceContentEntered && event == XMLStreamConstants.START_ELEMENT) {

						// uses the caption tag to identify filter from field description
						if (eventReader.getLocalName().equals("caption")) {

							String text = eventReader.getElementText();
							if (text.trim().startsWith("Filters")) {
								filtersEntered = true;

							} else {
								filtersEntered = false;
							}
							continue;
						}

						// look for potential resource names
						if (eventReader.getLocalName().equals("h4")) {
							if (resourceEntered) {
								classes.add(currentClass);
								System.out.println("Found class: " + currentClass.getName());
								resourceEntered = false;
							}
							String text = eventReader.getElementText();
							if (text.startsWith("Resource: /")) {

								Matcher m = resourcePattern.matcher(text);
								if (m.find()) {
									resourceEntered = true;
									currentClass = new GBClass(m.group(1));
									
									for (GBClass lookup : classes) {
										if (lookup.getName().equalsIgnoreCase(currentClass.getName()))
											resourceEntered = false;
									}
								}

							}
							continue;
						}

						// find filters
						if (resourceEntered && filtersEntered && eventReader.getLocalName().equals("td")) {
							
							// filter name first
							if (!fieldEntered) {
								String text = eventReader.getElementText();
								currentField = text.trim().replace("=", "");
								fieldEntered = true;
								//System.out.println("Filter entered: "+currentField);
								
								// filter description second
							} else if (fieldEntered) {
								//System.out.println("Filter exited");
								fieldEntered = false;
								descriptionEntered = true;
								currentDescription = "";
								//System.out.println("Description entered");
							}
							continue;
						}
						
					}
					
					// get rest of the description
					if (descriptionEntered && event == XMLStreamConstants.CHARACTERS){
						//System.out.println("Found description: "+eventReader.getText());
						currentDescription += eventReader.getText().trim() + " ";	
						continue;
					}
					
					
					// exited the description
					if (descriptionEntered && event == XMLStreamConstants.END_ELEMENT) {
						if (eventReader.getLocalName().equals("td")) {
							//System.out.println("Description exited");
							descriptionEntered = false;
							//currentDescription = currentDescription.substring(0, currentDescription.lastIndexOf(",")-1);
							//System.out.println("Added field: "+currentField+" "+currentDescription);
							currentClass.getFields().put(currentField, currentDescription);
							
							continue;
						}
					}
					
					// we're done and out
					if (event == XMLStreamConstants.END_DOCUMENT) {
						eventReader.close();
						break;
					}
				}
			} catch (XMLStreamException e) {
				e.printStackTrace();
			}
		}
	}
}
