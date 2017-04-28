/**
 * Copyright (c) 2015, STARSCHEMA LTD.
 * All rights reserved.

 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This class implements functions to store credentials in an xml file encrypted
 */

package net.starschema.clouddb.cmdlineverification;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Properties;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.starschema.clouddb.jdbc.Logger;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialStore;

// import net.starschema.clouddb.bqjdbc.logging.Logger;

/**
 * Implements CredentialStore to use XML file to store Refresh Tokens encrypted
 * with AES
 *
 * @author Horváth Attila
 *
 */
public class BQXMLCredentialStore implements CredentialStore {

    static Logger logger = Logger.getLogger(BQXMLCredentialStore.class
            .getName());

    /**
     * Decrypts AES encrypted byte array
     *
     * @param clientSecret
     *            The base for the key
     * @param argument
     *            to decrypt
     * @return decrypted byte array
     */
    public static byte[] decrypt(String clientSecret, byte[] argument) {
        byte[] ciphertext = argument;
        byte[] key = null;
        try {
            key = clientSecret.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
        MessageDigest sha = null;
        try {
            sha = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        key = sha.digest(key);
        key = Arrays.copyOf(key, 16); // using only first 128 bit
        SecretKey secret = new SecretKeySpec(key, "AES");
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        } catch (NoSuchAlgorithmException e) {
            return null;
        } catch (NoSuchPaddingException e) {
            return null;
        }
        try {
            cipher.init(Cipher.DECRYPT_MODE, secret);
        } catch (InvalidKeyException e) {
            return null;
        }
        try {
            ciphertext = cipher.doFinal(ciphertext);
        } catch (IllegalBlockSizeException e) {
            return null;
        } catch (BadPaddingException e) {
            return null;
        }
        return ciphertext;
    }

    /**
     * Encrypts a String argument with AES
     *
     * @param clientSecret
     *            the base for the key
     * @param argument
     *            the String to encrypt
     * @return encrypted byte array
     */
    public static byte[] encrypt(String clientSecret, String argument) {
        byte[] key = null;
        try {
            key = clientSecret.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
        MessageDigest sha = null;
        try {
            sha = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        key = sha.digest(key);
        key = Arrays.copyOf(key, 16); // using only first 128 bit
        SecretKey secret = new SecretKeySpec(key, "AES");

        /* encrypt the message. */
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        } catch (NoSuchAlgorithmException e) {
            return null;
        } catch (NoSuchPaddingException e) {
            return null;
        }
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secret);
        } catch (InvalidKeyException e) {
            return null;
        }
        try {
            return cipher.doFinal(argument.getBytes("UTF-8"));
        } catch (IllegalBlockSizeException e) {
            return null;
        } catch (BadPaddingException e) {
            return null;
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /**
     * Converts a String to its md5 hashed representation String
     *
     * @param md5
     * @return
     */
    public static String MD5(String md5) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest
                    .getInstance("MD5");
            byte[] array = md.digest(md5.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100)
                        .substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
        }
        return null;
    }

    String documentpath = null;

    /**
     * Constructor for the class, it just initializes the path to xml file from
     * the given properties file;
     *
     * @param Path
     * @throws IOException
     */
    public BQXMLCredentialStore(String path) throws IOException {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(System.getProperty("user.home")
                    + File.separator + ".bqjdbc" + File.separator
                    + "xmllocation.properties"));
        } catch (IOException e) {
            logger.debug("Property file not found for credential store location setting default location for credentials");
            this.documentpath = System.getProperty("user.home")
                    + File.separator + ".bqjdbc" + File.separator
                    + "credentials.xml";
        }
        String Check = properties.getProperty("xmlpath");
        if (Check == null) {
            logger.debug("The property xmlpath is not found in the properties file, setting default location");
            this.documentpath = System.getProperty("user.home")
                    + File.separator + ".bqjdbc" + File.separator
                    + "credentials.xml";
        } else {
            logger.debug(properties.getProperty("xmlpath"));
            if (properties.getProperty("xmlpath").contains("${user.home}")) {
                this.documentpath = System.getProperty("user.home")
                        + properties.getProperty("xmlpath").substring(
                        properties.getProperty("xmlpath").lastIndexOf(
                                "}") + 1);
            } else {
                this.documentpath = properties.getProperty("xmlpath");
            }
            logger.info("Document path for the credentials is: "
                    + this.documentpath);
        }
        // checking the path, making the directories if they doesn't exists
        File pathofdocumentpath = new File(
                new File(this.documentpath).getParent());
        if (!pathofdocumentpath.exists()) {
            pathofdocumentpath.mkdirs();
        }
    }

    @Override
    public void delete(String userId, Credential credential) throws IOException {
        Document doc = null;
        doc = this.loadDocument(this.documentpath);

        if (doc != null) {

            NodeList elements = doc.getElementsByTagName("Credential");
            Element loadelement = null;
            for (int i = 0; i < elements.getLength(); i++) {
                Node mynode = elements.item(i).getAttributes()
                        .getNamedItem("ID");
                if (mynode != null
                        && mynode.getNodeValue().equals(
                        BQXMLCredentialStore.MD5(userId))) {
                    loadelement = (Element) elements.item(i);
                }
            }
            if (loadelement != null) {
                loadelement.getParentNode().removeChild(loadelement);
            }
            TransformerFactory transformerFactory = TransformerFactory
                    .newInstance();
            Transformer transformer = null;
            try {
                transformer = transformerFactory.newTransformer();
            } catch (TransformerConfigurationException e) {
                throw new IOException(e);
            }
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(this.documentpath));
            try {
                transformer.transform(source, result);
            } catch (TransformerException e) {
                throw new IOException(e);
            }
        }
    }

    /**
     * <p>
     * <h1>Implementation Details:</h1><br>
     * Loads the refreshtoken into the credential from the xml file specified by
     * documentpath at ID userdId;
     * </p>
     */
    @Override
    public boolean load(String userId, Credential credential)
            throws IOException {
        Document doc = null;
        doc = this.loadOrCreateDocument(this.documentpath);

        String ClientSecret = userId.substring(userId.indexOf(":") + 1);

        NodeList elements = doc.getElementsByTagName("Credential");
        Element loadelement = null;
        String EncodedRefreshToken = null;
        for (int i = 0; i < elements.getLength(); i++) {
            Node mynode = elements.item(i).getAttributes().getNamedItem("ID");
            if (mynode != null
                    && mynode.getNodeValue().equals(
                    BQXMLCredentialStore.MD5(userId))) {
                loadelement = (Element) elements.item(i);
                EncodedRefreshToken = loadelement.getAttribute("Token");
            }
        }
        if (loadelement == null) {
            return false;
        }

        byte[] EncodedRefreshTokenBytes = EncodedRefreshToken.getBytes("UTF-8");
        byte[] Base64DecodedTokenBytes = org.apache.commons.codec.binary.Base64
                .decodeBase64(EncodedRefreshTokenBytes);
        byte[] decryptedRefreshTokenBytes = BQXMLCredentialStore.decrypt(
                ClientSecret, Base64DecodedTokenBytes);
        if (decryptedRefreshTokenBytes == null) {
            throw new IOException("Failed to decode RefreshToken");
        }

        credential.setExpiresInSeconds(null);
        credential.setExpirationTimeMilliseconds(null);
        credential.setRefreshToken(new String(decryptedRefreshTokenBytes,
                "UTF-8"));
        credential.setAccessToken(null);
        return true;
    }

    /**
     * Tries to load an XML document from path
     *
     * @param path
     * @return
     * @throws IOException
     */
    private Document loadDocument(String path) {
        InputStream file = null;
        try {
            file = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            return null;
        }
        DocumentBuilderFactory docFactory = DocumentBuilderFactory
                .newInstance();
        try {
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(file);
            return doc;
        } catch (ParserConfigurationException e1) {
            return null;
        } catch (SAXException e) {
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            try {
                file.close();
            } catch (IOException e) {
                logger.warn("Failed to close the credential store xml file.");
                return null;
            }
        }
    }

    /**
     * Either loads the xml document on the specified path, or if it does not
     * exists creates a new file with default infrastructure for storing
     * clientcredentials
     *
     * @param path
     * @return
     * @throws IOException
     */
    private Document loadOrCreateDocument(String path) throws IOException {
        Document doc = null;
        doc = this.loadDocument(path);
        if (doc == null) {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder docBuilder = null;
            try {
                docBuilder = docFactory.newDocumentBuilder();

                // root elements
                doc = docBuilder.newDocument();
                Element rootElement = doc.createElement("ClientCredentials");
                doc.appendChild(rootElement);

                TransformerFactory transformerFactory = TransformerFactory
                        .newInstance();
                Transformer transformer = null;
                transformer = transformerFactory.newTransformer();

                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult(new File(path));

                transformer.transform(source, result);
            } catch (ParserConfigurationException e1) {
                logger.warn("", e1);
                throw new IOException("Failed to load", e1);
            } catch (TransformerConfigurationException e) {
                logger.warn("", e);
                throw new IOException("Failed to load", e);
            } catch (TransformerException e) {
                logger.warn("", e);
                throw new IOException("Failed to load", e);
            }
        }
        return doc;
    }

    /**
     * <p>
     * <h1>Implementation Details:</h1><br>
     * Creates a new XML node named Credential, in the xml file Specified by
     * documentpath with attributes ID and Token. ID contains the
     * Clientid+Clientsecret MD5 hash, while Token contains Encrypted
     * Refreshtoken
     * </p>
     */
    @Override
    public void store(String userId, Credential credential) throws IOException {
        Document doc = null;
        logger.debug(this.documentpath);
        doc = this.loadOrCreateDocument(this.documentpath);

        NodeList elements = doc.getElementsByTagName("Credential");
        Element loadelement = null;
        for (int i = 0; i < elements.getLength(); i++) {
            Node mynode = elements.item(i).getAttributes().getNamedItem("ID");
            if (mynode != null
                    && mynode.getNodeValue().equals(
                    BQXMLCredentialStore.MD5(userId))) {
                loadelement = (Element) elements.item(i);
            }
        }
        if (loadelement != null) {
            String ClientSecret = userId.substring(userId.indexOf(":") + 1);
            byte[] RefreshTokenBytes = BQXMLCredentialStore.encrypt(
                    ClientSecret, credential.getRefreshToken());
            if (RefreshTokenBytes == null) {
                throw new IOException("Failed to encrypt RefreshToken");
            }
            byte[] base64encodedtoken = org.apache.commons.codec.binary.Base64
                    .encodeBase64(RefreshTokenBytes);
            String encryptedRefreshToken = new String(base64encodedtoken,
                    "UTF-8");
            loadelement.setAttribute("Token", encryptedRefreshToken);
        } else {
            Element rootElement = (Element) doc.getElementsByTagName(
                    "ClientCredentials").item(0);
            Element ClientCredential = doc.createElement("Credential");
            String ClientSecret = userId.substring(userId.indexOf(":") + 1);

            try {
                ClientCredential.setAttribute("ID",
                        BQXMLCredentialStore.MD5(userId));
                // ClientCredential.setIdAttribute("ID", true);
            } catch (DOMException e) {
                throw new IOException(e);
            }

            byte[] RefreshTokenBytes = BQXMLCredentialStore.encrypt(
                    ClientSecret, credential.getRefreshToken());
            if (RefreshTokenBytes == null) {
                throw new IOException("Failed to encrypt RefreshToken");
            }
            byte[] base64encodedtoken = org.apache.commons.codec.binary.Base64
                    .encodeBase64(RefreshTokenBytes);
            String encryptedRefreshToken = new String(base64encodedtoken,
                    "UTF-8");
            ClientCredential.setAttribute("Token", encryptedRefreshToken);
            rootElement.appendChild(ClientCredential);
        }
        TransformerFactory transformerFactory = TransformerFactory
                .newInstance();
        Transformer transformer = null;
        try {
            transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(this.documentpath));

            transformer.transform(source, result);
        } catch (TransformerConfigurationException e) {
            throw new IOException(e);
        } catch (TransformerException e) {
            throw new IOException(e);
        }
    }

}
