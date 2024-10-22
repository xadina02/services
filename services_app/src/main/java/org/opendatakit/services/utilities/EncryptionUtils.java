/*
 * Copyright (C) 2011-2013 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.services.utilities;

import org.opendatakit.logging.WebLogger;
import org.opendatakit.utilities.FileSet;
import org.opendatakit.utilities.FileSet.MimeFile;
import org.opendatakit.utilities.ODKFileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
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

/**
 * Utility class for encrypting submissions during the SaveToDiskTask.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class EncryptionUtils {
  private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
  private static final String t = "EncryptionUtils";
  public static final String RSA_ALGORITHM = "RSA";
  // the symmetric key we are encrypting with RSA is only 256 bits... use
  // SHA-256
  public static final String ASYMMETRIC_ALGORITHM = "RSA/NONE/OAEPWithSHA256AndMGF1Padding";
  public static final String SYMMETRIC_ALGORITHM = "AES/CFB/PKCS5Padding";
  public static final int SYMMETRIC_KEY_LENGTH = 256;
  public static final int IV_BYTE_LENGTH = 16;

  // tags in the submission manifest

  private static final String XML_ENCRYPTED_TAG_NAMESPACE = "http://www.opendatakit.org/xforms/encrypted";
  private static final String XML_OPENROSA_NAMESPACE = "http://openrosa.org/xforms";
  private static final String DATA = "data";
  private static final String ID = "id";
  private static final String VERSION = "version";
  private static final String ENCRYPTED = "encrypted";
  private static final String BASE64_ENCRYPTED_KEY = "base64EncryptedKey";
  private static final String ENCRYPTED_XML_FILE = "encryptedXmlFile";
  private static final String META = "meta";
  private static final String INSTANCE_ID = "instanceID";
  private static final String MEDIA = "media";
  private static final String FILE = "file";
  private static final String BASE64_ENCRYPTED_ELEMENT_SIGNATURE = "base64EncryptedElementSignature";
  private static final String NEW_LINE = "\n";

  private EncryptionUtils() {
  }

  public static final class EncryptedFormInformation {
    public final String appName;
    public final String tableId;
    public final String instanceId;
    public final String base64EncryptedFileRsaPublicKey;
    public final PublicKey rsaPublicKey;
    public final String base64RsaEncryptedSymmetricKey;
    public final SecretKeySpec symmetricKey;
    public final byte[] ivSeedArray;
    private int ivCounter = 0;
    public final StringBuilder elementSignatureSource = new StringBuilder();
    public final Base64Wrapper wrapper;

    EncryptedFormInformation(String appName, String tableId, String xmlBase64RsaPublicKey, String instanceId, PublicKey rsaPublicKey,
        Base64Wrapper wrapper) {
      this.appName = appName;
      this.tableId = tableId;
      this.instanceId = instanceId;
      this.base64EncryptedFileRsaPublicKey = xmlBase64RsaPublicKey;
      this.rsaPublicKey = rsaPublicKey;
      this.wrapper = wrapper;

      // generate the symmetric key from random bits...

      SecureRandom r = new SecureRandom();
      byte[] key = new byte[SYMMETRIC_KEY_LENGTH / 8];
      r.nextBytes(key);
      SecretKeySpec sk = new SecretKeySpec(key, SYMMETRIC_ALGORITHM);
      symmetricKey = sk;

      // construct the fixed portion of the iv -- the ivSeedArray
      // this is the md5 hash of the instanceID and the symmetric key
      try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(instanceId.getBytes(StandardCharsets.UTF_8));
        md.update(key);
        byte[] messageDigest = md.digest();
        ivSeedArray = new byte[IV_BYTE_LENGTH];
        for (int i = 0; i < IV_BYTE_LENGTH; ++i) {
          ivSeedArray[i] = messageDigest[(i % messageDigest.length)];
        }
      } catch (NoSuchAlgorithmException e) {
        WebLogger.getLogger(appName).e(t, e.toString());
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      }

      // construct the base64-encoded RSA-encrypted symmetric key
      try {
        Cipher pkCipher;
        pkCipher = Cipher.getInstance(ASYMMETRIC_ALGORITHM);
        // write AES key
        pkCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        byte[] pkEncryptedKey = pkCipher.doFinal(key);
        String alg = pkCipher.getAlgorithm();
        WebLogger.getLogger(appName).i(t, "AlgorithmUsed: " + alg);
        base64RsaEncryptedSymmetricKey = wrapper.encodeToString(pkEncryptedKey);

      } catch (NoSuchAlgorithmException e) {
        WebLogger.getLogger(appName).e(t, "Unable to encrypt the symmetric key");
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      } catch (NoSuchPaddingException e) {
        WebLogger.getLogger(appName).e(t, "Unable to encrypt the symmetric key");
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      } catch (InvalidKeyException e) {
        WebLogger.getLogger(appName).e(t, "Unable to encrypt the symmetric key");
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      } catch (IllegalBlockSizeException e) {
        WebLogger.getLogger(appName).e(t, "Unable to encrypt the symmetric key");
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      } catch (BadPaddingException e) {
        WebLogger.getLogger(appName).e(t, "Unable to encrypt the symmetric key");
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      }

      // start building elementSignatureSource...
      appendElementSignatureSource(tableId);
      appendElementSignatureSource(base64RsaEncryptedSymmetricKey);

      appendElementSignatureSource(instanceId);
    }

    public void appendElementSignatureSource(String value) {
      elementSignatureSource.append(value).append("\n");
    }

    public void appendSubmissionFileSignatureSource(String contents, File file) {
      String md5Hash = ODKFileUtils.getNakedMd5Hash(appName, contents);
      appendElementSignatureSource(file.getName() + "::" + md5Hash);
    }

    public void appendFileSignatureSource(File file) {
      String md5Hash = ODKFileUtils.getNakedMd5Hash(appName, file);
      appendElementSignatureSource(file.getName() + "::" + md5Hash);
    }

    public String getBase64EncryptedElementSignature() {
      // Step 0: construct the text of the elements in
      // elementSignatureSource (done)
      // Where...
      // * Elements are separated by newline characters.
      // * Filename is the unencrypted filename (no .enc suffix).
      // * Md5 hashes of the unencrypted files' contents are converted
      // to zero-padded 32-character strings before concatenation.
      // Assumes this is in the order:
      // formId
      // version (omitted if null)
      // base64RsaEncryptedSymmetricKey
      // instanceId
      // for each media file { filename "::" md5Hash }
      // submission.xml "::" md5Hash

      // Step 1: construct the (raw) md5 hash of Step 0.
      byte[] messageDigest;
      try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(elementSignatureSource.toString().getBytes(StandardCharsets.UTF_8));
        messageDigest = md.digest();
      } catch (NoSuchAlgorithmException e) {
        WebLogger.getLogger(appName).e(t, e.toString());
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      }

      // Step 2: construct the base64-encoded RSA-encrypted md5
      try {
        Cipher pkCipher;
        pkCipher = Cipher.getInstance(ASYMMETRIC_ALGORITHM);
        // write AES key
        pkCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
        byte[] pkEncryptedKey = pkCipher.doFinal(messageDigest);
        return wrapper.encodeToString(pkEncryptedKey);

      } catch (NoSuchAlgorithmException e) {
        WebLogger.getLogger(appName).e(t, "Unable to encrypt the symmetric key");
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      } catch (NoSuchPaddingException e) {
        WebLogger.getLogger(appName).e(t, "Unable to encrypt the symmetric key");
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      } catch (InvalidKeyException e) {
        WebLogger.getLogger(appName).e(t, "Unable to encrypt the symmetric key");
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      } catch (IllegalBlockSizeException e) {
        WebLogger.getLogger(appName).e(t, "Unable to encrypt the symmetric key");
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      } catch (BadPaddingException e) {
        WebLogger.getLogger(appName).e(t, "Unable to encrypt the symmetric key");
        WebLogger.getLogger(appName).printStackTrace(e);
        throw new IllegalArgumentException(e.getMessage());
      }
    }

    public Cipher getCipher() throws InvalidKeyException, InvalidAlgorithmParameterException,
        NoSuchAlgorithmException, NoSuchPaddingException {
      ++ivSeedArray[ivCounter % ivSeedArray.length];
      ++ivCounter;
      IvParameterSpec baseIv = new IvParameterSpec(ivSeedArray);
      Cipher c = Cipher.getInstance(EncryptionUtils.SYMMETRIC_ALGORITHM);
      c.init(Cipher.ENCRYPT_MODE, symmetricKey, baseIv);
      return c;
    }
  }

  /**
   * Retrieve the encryption information for this row.
   * 
   * @param appName
   * @param tableId
   * @param xmlBase64RsaPublicKey
   * @param instanceId
   * @return
   */
  public static EncryptedFormInformation getEncryptedFormInformation(String appName, String tableId, String xmlBase64RsaPublicKey, String instanceId) {

    // fetch the form information
    String base64RsaPublicKey = xmlBase64RsaPublicKey;
    PublicKey pk;
    Base64Wrapper wrapper;

    if (base64RsaPublicKey == null || base64RsaPublicKey.length() == 0) {
      return null; // this is legitimately not an encrypted form
    }

    // submission must have an OpenRosa metadata block with a non-null
    // instanceID value.
    if (instanceId == null) {
      WebLogger.getLogger(appName).e(t, "No OpenRosa metadata block or no instanceId defined in that block");
      return null;
    }

    int version = android.os.Build.VERSION.SDK_INT;
    if (version < 8) {
      WebLogger.getLogger(appName).e(t, "Phone does not support encryption.");
      return null; // save unencrypted
    }

    // this constructor will throw an exception if we are not
    // running on version 8 or above (if Base64 is not found).
    try {
      wrapper = new Base64Wrapper(appName);
    } catch (ClassNotFoundException e) {
      WebLogger.getLogger(appName).e(t, "Phone does not have Base64 class but API level is " + version);
      WebLogger.getLogger(appName).printStackTrace(e);
      return null; // save unencrypted
    }

    // OK -- Base64 decode (requires API Version 8 or higher)
    byte[] publicKey = wrapper.decode(base64RsaPublicKey);
    X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKey);
    KeyFactory kf;
    try {
      kf = KeyFactory.getInstance(RSA_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      WebLogger.getLogger(appName).e(t, "Phone does not support RSA encryption.");
      WebLogger.getLogger(appName).printStackTrace(e);
      return null;
    }
    try {
      pk = kf.generatePublic(publicKeySpec);
    } catch (InvalidKeySpecException e) {
      WebLogger.getLogger(appName).printStackTrace(e);
      WebLogger.getLogger(appName).e(t, "Invalid RSA public key.");
      return null;
    }
    return new EncryptedFormInformation(appName, tableId, xmlBase64RsaPublicKey, instanceId, pk, wrapper);
  }

  private static void encryptFile(File file, File encryptedFile, EncryptedFormInformation formInfo)
      throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
      InvalidAlgorithmParameterException {

    // add elementSignatureSource for this file...
    formInfo.appendFileSignatureSource(file);

    try {
      Cipher c = formInfo.getCipher();

      OutputStream fout;
      fout = new FileOutputStream(encryptedFile);
      fout = new CipherOutputStream(fout, c);
      InputStream fin;
      fin = new FileInputStream(file);
      byte[] buffer = new byte[2048];
      int len = fin.read(buffer);
      while (len != -1) {
        fout.write(buffer, 0, len);
        len = fin.read(buffer);
      }
      fin.close();
      fout.flush();
      fout.close();
      WebLogger.getLogger(formInfo.appName).i(t, "Encrpyted:" + file.getName() + " -> " + encryptedFile.getName());
    } catch (IOException e) {
      WebLogger.getLogger(formInfo.appName).e(t, "Error encrypting: " + file.getName() + " -> " + encryptedFile.getName());
      WebLogger.getLogger(formInfo.appName).printStackTrace(e);
      throw e;
    } catch (NoSuchAlgorithmException e) {
      WebLogger.getLogger(formInfo.appName).e(t, "Error encrypting: " + file.getName() + " -> " + encryptedFile.getName());
      WebLogger.getLogger(formInfo.appName).printStackTrace(e);
      throw e;
    } catch (NoSuchPaddingException e) {
      WebLogger.getLogger(formInfo.appName).e(t, "Error encrypting: " + file.getName() + " -> " + encryptedFile.getName());
      WebLogger.getLogger(formInfo.appName).printStackTrace(e);
      throw e;
    } catch (InvalidKeyException e) {
      WebLogger.getLogger(formInfo.appName).e(t, "Error encrypting: " + file.getName() + " -> " + encryptedFile.getName());
      WebLogger.getLogger(formInfo.appName).printStackTrace(e);
      throw e;
    } catch (InvalidAlgorithmParameterException e) {
      WebLogger.getLogger(formInfo.appName).e(t, "Error encrypting: " + file.getName() + " -> " + encryptedFile.getName());
      WebLogger.getLogger(formInfo.appName).printStackTrace(e);
      throw e;
    }
  }

  private static void encryptIntoFile(String contents, File submissionFile, File encryptedFile,
      EncryptedFormInformation formInfo) throws IOException, NoSuchAlgorithmException,
      NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {

    // add elementSignatureSource for this file...
    formInfo.appendSubmissionFileSignatureSource(contents, submissionFile);

    try {
      Cipher c = formInfo.getCipher();

      OutputStream fout;
      fout = new FileOutputStream(encryptedFile);
      fout = new CipherOutputStream(fout, c);
      InputStream fin;
      fin = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
      byte[] buffer = new byte[2048];
      int len = fin.read(buffer);
      while (len != -1) {
        fout.write(buffer, 0, len);
        len = fin.read(buffer);
      }
      fin.close();
      fout.flush();
      fout.close();
      WebLogger.getLogger(formInfo.appName).i(t, "Encrpyted: content -> " + encryptedFile.getName());
    } catch (IOException e) {
      WebLogger.getLogger(formInfo.appName).e(t, "Error encrypting: content -> " + encryptedFile.getName());
      WebLogger.getLogger(formInfo.appName).printStackTrace(e);
      throw e;
    } catch (NoSuchAlgorithmException e) {
      WebLogger.getLogger(formInfo.appName).e(t, "Error encrypting: content -> " + encryptedFile.getName());
      WebLogger.getLogger(formInfo.appName).printStackTrace(e);
      throw e;
    } catch (NoSuchPaddingException e) {
      WebLogger.getLogger(formInfo.appName).e(t, "Error encrypting: content -> " + encryptedFile.getName());
      WebLogger.getLogger(formInfo.appName).printStackTrace(e);
      throw e;
    } catch (InvalidKeyException e) {
      WebLogger.getLogger(formInfo.appName).e(t, "Error encrypting: content -> " + encryptedFile.getName());
      WebLogger.getLogger(formInfo.appName).printStackTrace(e);
      throw e;
    } catch (InvalidAlgorithmParameterException e) {
      WebLogger.getLogger(formInfo.appName).e(t, "Error encrypting: content -> " + encryptedFile.getName());
      WebLogger.getLogger(formInfo.appName).printStackTrace(e);
      throw e;
    }
  }

  public static boolean deletePlaintextFiles(File instanceXml) {
    // NOTE: assume the directory containing the instanceXml contains ONLY
    // files related to this one instance.
    File instanceDir = instanceXml.getParentFile();

    boolean allSuccessful = true;
    // encrypt files that do not end with ".enc", and do not start with ".";
    // ignore directories
    File[] allFiles = instanceDir.listFiles();
    for (File f : allFiles) {
      if (f.equals(instanceXml))
        continue; // don't touch instance file
      if (f.isDirectory())
        continue; // don't handle directories
      if (!f.getName().endsWith(".enc")) {
        // not an encrypted file -- delete it!
        allSuccessful = allSuccessful & f.delete(); // DO NOT
        // short-circuit
      }
    }
    return allSuccessful;
  }

  private static List<MimeFile> encryptSubmissionFiles(FileSet fileSet, String submission,
                                                       File submissionXml, File submissionXmlEnc, EncryptedFormInformation formInfo) {

    // encrypt files that do not end with ".enc"
    List<MimeFile> filesToProcess = new ArrayList<MimeFile>();
    for (MimeFile f : fileSet.attachmentFiles) {
      if (f.file.getName().endsWith(".enc")) {
        f.file.delete(); // try to delete this (leftover junk)
      } else {
        filesToProcess.add(f);
      }
    }
    // encrypt here...
    for (MimeFile f : filesToProcess) {
      try {
        File encryptedFile = new File(f.file.getParentFile(), f.file.getName() + ".enc");
        encryptFile(f.file, encryptedFile, formInfo);
        f.file = encryptedFile;
        f.contentType = APPLICATION_OCTET_STREAM;
      } catch (IOException e) {
        return null;
      } catch (InvalidKeyException e) {
        return null;
      } catch (NoSuchAlgorithmException e) {
        return null;
      } catch (NoSuchPaddingException e) {
        return null;
      } catch (InvalidAlgorithmParameterException e) {
        return null;
      }
    }

    // encrypt the submission.xml as the last file...
    try {
      encryptIntoFile(submission, submissionXml, submissionXmlEnc, formInfo);
      // TODO: attachments remain in plaintext on the sdcard until
      // instance is deleted
      fileSet.addAttachmentFile(submissionXmlEnc, APPLICATION_OCTET_STREAM);
    } catch (IOException e) {
      return null;
    } catch (InvalidKeyException e) {
      return null;
    } catch (NoSuchAlgorithmException e) {
      return null;
    } catch (NoSuchPaddingException e) {
      return null;
    } catch (InvalidAlgorithmParameterException e) {
      return null;
    }

    return filesToProcess;
  }

  /**
   * Constructs the encrypted attachments, encrypted form xml, and the plaintext
   * submission manifest (with signature) for the form submission.
   *
   * Does not delete any of the original files.
   *
   * @parma fileSet
   * @param submission
   * @param submissionXml
   * @param submissionXmlEnc
   * @param formInfo
   * @return
   */
  public static boolean generateEncryptedSubmission(FileSet fileSet, String submission,
      File submissionXml, File submissionXmlEnc, EncryptedFormInformation formInfo) {

    // Step 1: encrypt the submission and all the media files...
    List<MimeFile> mediaFiles = encryptSubmissionFiles(fileSet, submission, submissionXml,
        submissionXmlEnc, formInfo);
    if (mediaFiles == null) {
      return false; // something failed...
    }

    // Step 2: build the encrypted-submission manifest (overwrites
    // submission.xml)...
    if (!writeSubmissionManifest(formInfo, submissionXml, submissionXmlEnc, mediaFiles)) {
      return false;
    }
    fileSet.instanceFile = submissionXml;
    return true;
  }

  private static boolean writeSubmissionManifest(EncryptedFormInformation formInfo,
      File submissionXml, File submissionXmlEnc, List<MimeFile> mediaFiles) {

    FileOutputStream out = null;
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document d = db.newDocument();
      d.setXmlStandalone(true);
      Element e = d.createElementNS(XML_ENCRYPTED_TAG_NAMESPACE, DATA);
      e.setPrefix(null);
      e.setAttribute(ID, formInfo.tableId);
      e.setAttribute(ENCRYPTED, "yes");
      d.appendChild(e);
  
      Element c;
      c = d.createElementNS(XML_ENCRYPTED_TAG_NAMESPACE, BASE64_ENCRYPTED_KEY);
      Text txtNode;
      txtNode = d.createTextNode(formInfo.base64RsaEncryptedSymmetricKey);
      c.appendChild(txtNode);
      e.appendChild(c);
  
      c = d.createElementNS(XML_OPENROSA_NAMESPACE, META);
      c.setPrefix("orx");
      {
        Element instanceTag = d.createElementNS(XML_OPENROSA_NAMESPACE, INSTANCE_ID);
        txtNode = d.createTextNode(formInfo.instanceId);
        instanceTag.appendChild(txtNode);
        c.appendChild(instanceTag);
      }
      e.appendChild(c);
  
      for (MimeFile file : mediaFiles) {
        c = d.createElementNS(XML_ENCRYPTED_TAG_NAMESPACE, MEDIA);
        Element fileTag = d.createElementNS(XML_ENCRYPTED_TAG_NAMESPACE, FILE);
        txtNode = d.createTextNode(file.file.getName());
        fileTag.appendChild(txtNode);
        c.appendChild(fileTag);
        e.appendChild(c);
      }
  
      c = d.createElementNS(XML_ENCRYPTED_TAG_NAMESPACE, ENCRYPTED_XML_FILE);
      txtNode = d.createTextNode(submissionXmlEnc.getName());
      c.appendChild(txtNode);
      e.appendChild(c);
  
      c = d.createElementNS(XML_ENCRYPTED_TAG_NAMESPACE, BASE64_ENCRYPTED_ELEMENT_SIGNATURE);
      txtNode = d.createTextNode(formInfo.getBase64EncryptedElementSignature());
      c.appendChild(txtNode);
      e.appendChild(c);

      out = new FileOutputStream(submissionXml);

      TransformerFactory factory = TransformerFactory.newInstance();
      Transformer transformer = factory.newTransformer();
      Properties outFormat = new Properties();
      outFormat.setProperty( OutputKeys.INDENT, "no" );
      outFormat.setProperty( OutputKeys.METHOD, "xml" );
      outFormat.setProperty( OutputKeys.OMIT_XML_DECLARATION, "yes" );
      outFormat.setProperty( OutputKeys.VERSION, "1.0" );
      outFormat.setProperty( OutputKeys.ENCODING, "UTF-8" );
      transformer.setOutputProperties( outFormat );

      DOMSource domSource = new DOMSource( d.getDocumentElement() );
      StreamResult result = new StreamResult( out );
      transformer.transform( domSource, result );

      out.flush();
      out.close();
    } catch (FileNotFoundException ex) {
      WebLogger.getLogger(formInfo.appName).printStackTrace(ex);
      WebLogger.getLogger(formInfo.appName).e(t, "Error writing submission.xml for encrypted submission: "
          + submissionXml.getParentFile().getName());
      return false;
    } catch (UnsupportedEncodingException ex) {
      WebLogger.getLogger(formInfo.appName).printStackTrace(ex);
      WebLogger.getLogger(formInfo.appName).e(t, "Error writing submission.xml for encrypted submission: "
          + submissionXml.getParentFile().getName());
      return false;
    } catch (IOException ex) {
      WebLogger.getLogger(formInfo.appName).printStackTrace(ex);
      WebLogger.getLogger(formInfo.appName).e(t, "Error writing submission.xml for encrypted submission: "
          + submissionXml.getParentFile().getName());
      return false;
    } catch (TransformerConfigurationException ex) {
      WebLogger.getLogger(formInfo.appName).printStackTrace(ex);
      WebLogger.getLogger(formInfo.appName).e(t, "Error writing submission.xml for encrypted submission: "
          + submissionXml.getParentFile().getName());
      return false;
    } catch (TransformerException ex) {
      WebLogger.getLogger(formInfo.appName).printStackTrace(ex);
      WebLogger.getLogger(formInfo.appName).e(t, "Error writing submission.xml for encrypted submission: "
          + submissionXml.getParentFile().getName());
      return false;
    } catch (ParserConfigurationException ex) {
      WebLogger.getLogger(formInfo.appName).printStackTrace(ex);
      WebLogger.getLogger(formInfo.appName).e(t, "Error writing submission.xml for encrypted submission: "
          + submissionXml.getParentFile().getName());
      return false;
    } finally {
      if ( out != null ) {
        try {
          out.close();
        } catch ( IOException e) {
          // ignore
        }
      }
    }

    return true;
  }
}
