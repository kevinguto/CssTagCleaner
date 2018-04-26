import javax.naming.Name;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static Matcher htmlPageMatcher;

    private static Pattern cssBlockPattern= Pattern.compile("(?<=}|^)\\s*([^{}@]+?)\\s*([{][^{}]+[}](?!\\s*[}]))", Pattern.DOTALL);
    private static Pattern cssTagPattern = Pattern.compile("[.#]-?[_a-zA-Z][_a-zA-Z0-9-]+");
    private static Pattern mediaPattern = Pattern.compile("(@media[^{]+)[{](.+?})\\s*}", Pattern.DOTALL);
    private static Pattern keyFramePattern = Pattern.compile("@keyframes\\s*([^{]+)[{](.+?})\\s*}", Pattern.DOTALL);
    private static Pattern classIdTagPattern= Pattern.compile("(class|id)\\s*=\\s*[\"']([^'\"]+)['\"]", Pattern.DOTALL);

    private static int currentTagNumber = 0;
    private static String commonTag ="replacedTag";
    private static int currentDeletedTagNumber= 0;
    private static String commonDeletedTag ="deletedTag";

    private static HashMap<String, String> replacedTags = new HashMap<>();
    private static HashMap<String, String> deletedTags= new HashMap<>();

    private static String cssOutDir= "css/";
    private static String htmlPageContent;
    private static boolean keepKeyFramesInCss= false;


    public static void main(String args[]) throws IOException {
        String htmlPagePath="";
        htmlPageContent = new String(Files.readAllBytes(Paths.get(htmlPagePath)), StandardCharsets.UTF_8);
        htmlPageMatcher= Pattern.compile("").matcher(htmlPageContent);

        //
        String cssDir= "";
        File[] cssFiles = new File(cssDir).listFiles();
        new File("css").mkdir();

        assert cssFiles != null;
        for (File cssFile : cssFiles) {
            if(cssFile.getName().endsWith(".css"))
                handleCssFile(cssFile);
        }

        BufferedWriter deletedTagsWriter= new BufferedWriter(new FileWriter(new File("deletedTags.txt")));
        BufferedWriter replacedTagsWriter= new BufferedWriter(new FileWriter(new File("replacedTags.txt")));

        for (String s : replacedTags.keySet())
            replacedTagsWriter.write(s+"\t"+replacedTags.get(s)+System.getProperty("line.separator"));

        for (String s : deletedTags.keySet())
            deletedTagsWriter.write(s+"\t"+deletedTags.get(s)+System.getProperty("line.separator"));

        replacedTagsWriter.close();
        deletedTagsWriter.close();

        cleanDanglingClassIdTags();

        PrintWriter htmlPrintWriter = new PrintWriter("index.html");
        htmlPrintWriter.write(htmlPageContent);
        htmlPrintWriter.close();

        Runtime.getRuntime().exec("explorer .");
    }

    private static void cleanDanglingClassIdTags() throws IOException {
        BufferedReader stringReader= new BufferedReader(new StringReader(htmlPageContent));

        StringBuilder newHtmlPageContent= new StringBuilder();

        String line;
        while((line=stringReader.readLine())!=null){
            if(!line.contains("class") && !line.contains("id")){
                newHtmlPageContent.append(line).append(System.getProperty("line.separator"));
            }else{
                StringBuffer newLine= new StringBuffer();
                Matcher classIdTagMatcher = classIdTagPattern.matcher(line);
                while (classIdTagMatcher.find()){
                    String tagContent = classIdTagMatcher.group(2);
                    String[] names = tagContent.split("\\s+");
                    String newNames="";
                    for (String name : names) if(name.startsWith("mt") || name.startsWith("mdt")) newNames+=name+" ";
                    String newTagFormat= newNames.isEmpty()?"":classIdTagMatcher.group(1)+"=\""+newNames.trim()+"\"";
                    classIdTagMatcher.appendReplacement(newLine, newTagFormat);
                }
                classIdTagMatcher.appendTail(newLine);
                newHtmlPageContent.append(newLine).append(System.getProperty("line.separator"));
            }
        }

        stringReader.close();
        htmlPageContent=newHtmlPageContent.toString();
    }

    private static void handleCssFile(File cssFile) throws IOException {
        String cssContent = new String(Files.readAllBytes(Paths.get(cssFile.getPath())), StandardCharsets.UTF_8);

        File file = new File(cssOutDir + cssFile.getName());
        file.createNewFile();
        BufferedWriter cssFileWriter= new BufferedWriter(new FileWriter(file));

        StringBuffer newCssContent= new StringBuffer();
        Matcher mediaBlockMatcher = mediaPattern.matcher(cssContent);
        while(mediaBlockMatcher.find()){
            String mediaBlockContent = mediaBlockMatcher.group(2);
            Matcher mediaCssMatcher = cssBlockPattern.matcher(mediaBlockContent);

            StringBuilder newMediaBlockContent= new StringBuilder();
            while(mediaCssMatcher.find()){
                String cssBlockTags = mediaCssMatcher.group(1);
                String cssBlockTagsResult = handleCssBlockTags(cssBlockTags);

                if(cssBlockTagsResult.isEmpty()) continue;

                newMediaBlockContent.append(cssBlockTagsResult);
                newMediaBlockContent.append(mediaCssMatcher.group(2));
                newMediaBlockContent.append(System.getProperty("line.separator"));
            }

            if(newMediaBlockContent.length()==0) continue;

            cssFileWriter.write(mediaBlockMatcher.group(1));
            cssFileWriter.write("{"+System.getProperty("line.separator")+newMediaBlockContent.toString());
            cssFileWriter.write(System.getProperty("line.separator")+"}");
            cssFileWriter.write(System.getProperty("line.separator"));
            mediaBlockMatcher.appendReplacement(newCssContent, "");
        }
        mediaBlockMatcher.appendTail(newCssContent);

        StringBuffer newCssContent2= new StringBuffer();
        Matcher keyFramesBlockMatcher = keyFramePattern.matcher(newCssContent.toString());
        while (keyFramesBlockMatcher.find()) {
            if (keepKeyFramesInCss){
                cssFileWriter.write(keyFramesBlockMatcher.group());
                cssFileWriter.write(System.getProperty("line.separator"));
             }
            keyFramesBlockMatcher.appendReplacement(newCssContent2, "");
        }
        keyFramesBlockMatcher.appendTail(newCssContent2);

        Matcher cssMatcher = cssBlockPattern.matcher(newCssContent2.toString());
        while(cssMatcher.find()){
            String cssBlockTags = cssMatcher.group(1);
            String cssBlockTagsResult = handleCssBlockTags(cssBlockTags);

            if(cssBlockTagsResult.isEmpty()) continue;

            cssFileWriter.write(cssBlockTagsResult);
            cssFileWriter.write(cssMatcher.group(2));
            cssFileWriter.write(System.getProperty("line.separator"));
        }


        cssFileWriter.close();
    }

    private static String handleCssBlockTags(String cssBlockTags) {
        Matcher cssTagMatcher = cssTagPattern.matcher(cssBlockTags);
        String finalTags= "";

        if(cssTagMatcher.find())
        {
            String[] tagsParts = cssBlockTags.split(",");

            for (String tagsPart : tagsParts) {
                Matcher tagsPartMatcher = cssTagPattern.matcher(tagsPart);
                if(!tagsPartMatcher.find()){
                    finalTags+= ","+tagsPart;
                }else{
                    StringBuffer newTagPart= new StringBuffer();
                    boolean allTagsExistInHtml= true;
                    do{
                        Combo tagCombo = handleCssTag(tagsPartMatcher.group());
                        tagsPartMatcher.appendReplacement(newTagPart, tagCombo.newTag);
                        allTagsExistInHtml = allTagsExistInHtml && tagCombo.existsInHtml;
                    }while (tagsPartMatcher.find());
                    tagsPartMatcher.appendTail(newTagPart);

                    if(allTagsExistInHtml)
                        finalTags+= ","+newTagPart.toString();
                }
            }
        }else{
            return cssBlockTags;
        }

        return finalTags.length()==0?"":finalTags.substring(1);
    }

    private static Combo handleCssTag(String cssTag) {
        String htmltag= (cssTag.charAt(0)=='.'?"class":"id");
        String tagContent= (cssTag.charAt(0)=='.'?".*?":"");
        String tagType= String.valueOf(cssTag.charAt(0));
        cssTag= cssTag.substring(1);

        if(deletedTags.containsKey(cssTag)) return new Combo(tagType+deletedTags.get(cssTag), false);
        if(replacedTags.containsKey(cssTag)) return new Combo(tagType+replacedTags.get(cssTag), true);

        String regexString= "("+htmltag+"=.*?(?:['\"]|\\s))"+cssTag+"((?:['\"]|\\s))";
        htmlPageMatcher.usePattern(Pattern.compile(regexString)).reset();

        htmlPageMatcher.reset();
        if(!htmlPageMatcher.find()){
            String newTag=commonDeletedTag + currentDeletedTagNumber;
            currentDeletedTagNumber++;

            deletedTags.put(cssTag, newTag);
            return new Combo(tagType+newTag, false);
        }

        String newTag=commonTag + currentTagNumber;
        currentTagNumber++;
        htmlPageContent= htmlPageMatcher.replaceAll("$1"+newTag+"$2");
        htmlPageMatcher.reset(htmlPageContent);
        replacedTags.put(cssTag, newTag);

        return new Combo(tagType+newTag, true);
    }

    private static class Combo{
        String newTag;
        boolean existsInHtml;

        public Combo(String newTag, boolean existsInHtml) {
            this.newTag = newTag;
            this.existsInHtml = existsInHtml;
        }
    }
}
