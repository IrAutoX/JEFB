package ir.IrAutoX.JEFB;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Properties;

public final class SignMeta {

    public final String company;
    public final String author;
    public final String issued;
    public final String expires;
    public final String license;
    public final String signature;
    public final String projectHash;

    public SignMeta(String company, String author, String issued, String expires,
                    String license, String signature, String projectHash) {
        this.company = company;
        this.author = author;
        this.issued = issued;
        this.expires = expires;
        this.license = license;
        this.signature = signature;
        this.projectHash = projectHash;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("# JEFB METADATA v3\n");
        sb.append("company=").append(company).append("\n");
        sb.append("author=").append(author).append("\n");
        sb.append("issued=").append(issued).append("\n");
        sb.append("expires=").append(expires).append("\n");
        sb.append("license=").append(license).append("\n");
        sb.append("projectHash=").append(projectHash).append("\n");
        sb.append("signature=").append(signature).append("\n");
        return sb.toString();
    }

    public static SignMeta parse(String content) {
        Properties p = new Properties();
        try (Reader r = new StringReader(content)) { p.load(r); }
        catch (IOException ignored) {}
        return new SignMeta(
                p.getProperty("company", "").trim(),
                p.getProperty("author", "").trim(),
                p.getProperty("issued", "").trim(),
                p.getProperty("expires", "").trim(),
                p.getProperty("license", "").trim(),
                p.getProperty("signature", "").trim(),
                p.getProperty("projectHash", "").trim()
        );
    }
}