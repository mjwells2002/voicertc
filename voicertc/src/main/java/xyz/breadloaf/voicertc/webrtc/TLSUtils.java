package xyz.breadloaf.voicertc.webrtc;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCrypto;
import org.bouncycastle.tls.crypto.impl.jcajce.JceDefaultTlsCredentialedDecryptor;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Hashtable;

public class TLSUtils
{
    static TlsCredentialedDecryptor loadEncryptionCredentials(TlsContext context, String[] certResources, String keyResource) throws IOException
    {
        TlsCrypto crypto = context.getCrypto();
        Certificate certificate = loadCertificateChain(context,certResources);

        if (crypto instanceof BcTlsCrypto)
        {
            AsymmetricKeyParameter privateKey = loadBcPrivateKeyResource(keyResource);

            return new BcDefaultTlsCredentialedDecryptor((BcTlsCrypto)crypto, certificate, privateKey);
        }
        else
        {
            JcaTlsCrypto jcaCrypto = (JcaTlsCrypto)crypto;
            PrivateKey privateKey = loadJcaPrivateKeyResource(jcaCrypto, keyResource);

            return new JceDefaultTlsCredentialedDecryptor(jcaCrypto, certificate, privateKey);
        }
    }
    static TlsCredentialedSigner loadSignerCredentials(TlsContext context, String[] certResources, String keyResource, SignatureAndHashAlgorithm signatureAndHashAlgorithm) throws IOException
    {
        TlsCrypto crypto = context.getCrypto();
        Certificate certificate = loadCertificateChain(context, certResources);
        TlsCryptoParameters cryptoParams = new TlsCryptoParameters(context);

        if (crypto instanceof BcTlsCrypto)
        {
            AsymmetricKeyParameter privateKey = loadBcPrivateKeyResource(keyResource);

            return new BcDefaultTlsCredentialedSigner(cryptoParams, (BcTlsCrypto)crypto, privateKey, certificate, signatureAndHashAlgorithm);
        }
        else
        {
            JcaTlsCrypto jcaCrypto = (JcaTlsCrypto)crypto;
            PrivateKey privateKey = loadJcaPrivateKeyResource(jcaCrypto, keyResource);

            return new JcaDefaultTlsCredentialedSigner(cryptoParams, jcaCrypto, privateKey, certificate, signatureAndHashAlgorithm);
        }
    }
    static Certificate loadCertificateChain(TlsContext context, String[] resources) throws IOException
    {
        TlsCrypto crypto = context.getCrypto();

        if (TlsUtils.isTLSv13(context))
        {
            CertificateEntry[] certificateEntryList = new CertificateEntry[resources.length];
            for (int i = 0; i < resources.length; ++i)
            {
                TlsCertificate certificate = loadCertificateResource(crypto, resources[i]);

                // TODO[tls13] Add possibility of specifying e.g. CertificateStatus
                Hashtable extensions = null;

                certificateEntryList[i] = new CertificateEntry(certificate, extensions);
            }

            // TODO[tls13] Support for non-empty request context
            byte[] certificateRequestContext = TlsUtils.EMPTY_BYTES;

            return new Certificate(certificateRequestContext, certificateEntryList);
        }
        else
        {
            TlsCertificate[] chain = new TlsCertificate[resources.length];
            for (int i = 0; i < resources.length; ++i)
            {
                chain[i] = loadCertificateResource(crypto, resources[i]);
            }
            return new Certificate(chain);
        }
    }

    static TlsCertificate loadCertificateResource(TlsCrypto crypto, String resource) throws IOException
    {
        PemObject pem = loadPemResource(resource);
        if (pem.getType().endsWith("CERTIFICATE"))
        {
            return crypto.createCertificate(pem.getContent());
        }
        throw new IllegalArgumentException("'resource' doesn't specify a valid certificate");
    }

    static AsymmetricKeyParameter loadBcPrivateKeyResource(String resource)
            throws IOException
    {
        PemObject pem = loadPemResource(resource);
        if (pem.getType().equals("PRIVATE KEY"))
        {
            return PrivateKeyFactory.createKey(pem.getContent());
        }
        if (pem.getType().equals("ENCRYPTED PRIVATE KEY"))
        {
            throw new UnsupportedOperationException("Encrypted PKCS#8 keys not supported");
        }
        if (pem.getType().equals("RSA PRIVATE KEY"))
        {
            RSAPrivateKey rsa = RSAPrivateKey.getInstance(pem.getContent());
            return new RSAPrivateCrtKeyParameters(rsa.getModulus(), rsa.getPublicExponent(),
                    rsa.getPrivateExponent(), rsa.getPrime1(), rsa.getPrime2(), rsa.getExponent1(),
                    rsa.getExponent2(), rsa.getCoefficient());
        }
        if (pem.getType().equals("EC PRIVATE KEY"))
        {
            ECPrivateKey pKey = ECPrivateKey.getInstance(pem.getContent());
            AlgorithmIdentifier algId = new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, pKey.getParameters());
            PrivateKeyInfo privInfo = new PrivateKeyInfo(algId, pKey);
            return PrivateKeyFactory.createKey(privInfo);
        }
        throw new IllegalArgumentException("'resource' doesn't specify a valid private key");
    }

    static PrivateKey loadJcaPrivateKeyResource(JcaTlsCrypto crypto, String resource) throws IOException
    {
        Throwable cause = null;
        try
        {
            PemObject pem = loadPemResource(resource);
            if (pem.getType().equals("PRIVATE KEY"))
            {
                return loadJcaPkcs8PrivateKey(crypto, pem.getContent());
            }
            if (pem.getType().equals("ENCRYPTED PRIVATE KEY"))
            {
                throw new UnsupportedOperationException("Encrypted PKCS#8 keys not supported");
            }
            if (pem.getType().equals("RSA PRIVATE KEY"))
            {
                RSAPrivateKey rsa = RSAPrivateKey.getInstance(pem.getContent());
                KeyFactory keyFact = crypto.getHelper().createKeyFactory("RSA");
                return keyFact.generatePrivate(new RSAPrivateCrtKeySpec(rsa.getModulus(), rsa.getPublicExponent(),
                        rsa.getPrivateExponent(), rsa.getPrime1(), rsa.getPrime2(), rsa.getExponent1(), rsa.getExponent2(),
                        rsa.getCoefficient()));
            }
        }
        catch (GeneralSecurityException e)
        {
            cause = e;
        }
        throw new IllegalArgumentException("'resource' doesn't specify a valid private key", cause);
    }

    static PrivateKey loadJcaPkcs8PrivateKey(JcaTlsCrypto crypto, byte[] encoded) throws GeneralSecurityException
    {
        PrivateKeyInfo pki = PrivateKeyInfo.getInstance(encoded);
        AlgorithmIdentifier algID = pki.getPrivateKeyAlgorithm();
        ASN1ObjectIdentifier oid = algID.getAlgorithm();

        String name;
        /*if (X9ObjectIdentifiers.id_dsa.equals(oid))
        {
            name = "DSA";
        }
        else if (X9ObjectIdentifiers.id_ecPublicKey.equals(oid))
        {
            name = "EC";
        }
        else */if (PKCSObjectIdentifiers.rsaEncryption.equals(oid) || PKCSObjectIdentifiers.id_RSASSA_PSS.equals(oid))
        {
            name = "RSA";
        }
        /*else if (EdECObjectIdentifiers.id_Ed25519.equals(oid))
        {
            name = "Ed25519";
        }
        else if (EdECObjectIdentifiers.id_Ed448.equals(oid))
        {
            name = "Ed448";
        }*/
        else
        {
            name = oid.getId();
        }
        KeyFactory kf = crypto.getHelper().createKeyFactory(name);
        return kf.generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    static PemObject loadPemResource(String resource) throws IOException
    {
        InputStream s = new FileInputStream(resource);
        PemReader p = new PemReader(new InputStreamReader(s));
        PemObject o = p.readPemObject();
        p.close();
        return o;
    }
}
