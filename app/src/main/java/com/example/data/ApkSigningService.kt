package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class KeystoreFileInfo(
  val name: String,
  val absolutePath: String,
  val sizeBytes: Long,
  val lastModifiedMs: Long,
)

data class ApkVerificationSummary(
  val apkName: String,
  val apkPath: String,
  val sizeBytes: Long,
  val isVerified: Boolean,
  val verifiedV1: Boolean,
  val verifiedV2: Boolean,
  val verifiedV3: Boolean,
  val signerSha256: String?,
  val signerSubject: String?,
  val errorMessage: String?,
)

data class SigningResult(
  val signedApkPath: String,
  val signedApkName: String,
  val verification: ApkVerificationSummary,
  val durationMs: Long,
)

class ApkSigningService(private val context: Context) {
  val keystoresDir: File = File(context.filesDir, "keystores").apply { mkdirs() }
  val apksDir: File = File(context.filesDir, "apks").apply { mkdirs() }

  suspend fun listKeystores(): List<KeystoreFileInfo> =
    withContext(Dispatchers.IO) {
      keystoresDir
        .listFiles()
        ?.filter { it.isFile }
        ?.sortedByDescending { it.lastModified() }
        ?.map {
          KeystoreFileInfo(
            name = it.name,
            absolutePath = it.absolutePath,
            sizeBytes = it.length(),
            lastModifiedMs = it.lastModified(),
          )
        }
        ?: emptyList()
    }

  suspend fun listAvailableApks(workspacesDir: File): List<File> =
    withContext(Dispatchers.IO) {
      val fromApks = apksDir.listFiles()?.filter { it.isFile && it.extension.equals("apk", true) } ?: emptyList()
      val fromWorkspaces =
        workspacesDir
          .walkTopDown()
          .filter { it.isFile && it.extension.equals("apk", true) }
          .toList()
      (fromApks + fromWorkspaces)
        .distinctBy { it.canonicalPath }
        .sortedByDescending { it.lastModified() }
    }

  suspend fun importApkFromUri(uri: Uri): File =
    withContext(Dispatchers.IO) {
      val name = queryDisplayName(uri) ?: "imported_${System.currentTimeMillis()}.apk"
      val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
      val finalName = if (safeName.endsWith(".apk", true)) safeName else "$safeName.apk"
      val dest = File(apksDir, finalName)
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output) }
      } ?: throw IllegalStateException("Cannot open selected APK file.")
      dest
    }

  suspend fun importKeystoreFromUri(uri: Uri): KeystoreFileInfo =
    withContext(Dispatchers.IO) {
      val name = queryDisplayName(uri) ?: "keystore_${System.currentTimeMillis()}.p12"
      val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
      val dest = File(keystoresDir, safeName)
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output) }
      } ?: throw IllegalStateException("Cannot open selected keystore file.")
      KeystoreFileInfo(
        name = dest.name,
        absolutePath = dest.absolutePath,
        sizeBytes = dest.length(),
        lastModifiedMs = dest.lastModified(),
      )
    }

  suspend fun generatePkcs12Keystore(
    fileName: String,
    alias: String,
    commonName: String,
    organization: String,
    password: String,
    validityYears: Int = 10,
  ): KeystoreFileInfo =
    withContext(Dispatchers.IO) {
      require(alias.isNotBlank()) { "Key alias cannot be empty." }
      require(password.length >= 6) { "Keystore password must be at least 6 characters." }

      val cleanFile =
        fileName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "release_key" }
      val finalFile =
        if (cleanFile.endsWith(".p12", true) || cleanFile.endsWith(".pfx", true)) cleanFile
        else "$cleanFile.p12"
      val outFile = File(keystoresDir, finalFile)

      val keyGen = KeyPairGenerator.getInstance("RSA")
      keyGen.initialize(2048, SecureRandom())
      val keyPair = keyGen.generateKeyPair()

      val cert =
        buildSelfSignedX509Certificate(
          publicKeyEncoded = keyPair.public.encoded,
          privateKey = keyPair.private,
          commonName = commonName.ifBlank { "TermForge Release" },
          organization = organization.ifBlank { "TermForge" },
          validityYears = validityYears.coerceIn(1, 20),
        )

      val ks = KeyStore.getInstance("PKCS12")
      ks.load(null, null)
      ks.setKeyEntry(alias.trim(), keyPair.private, password.toCharArray(), arrayOf(cert))
      FileOutputStream(outFile).use { fos -> ks.store(fos, password.toCharArray()) }

      KeystoreFileInfo(
        name = outFile.name,
        absolutePath = outFile.absolutePath,
        sizeBytes = outFile.length(),
        lastModifiedMs = outFile.lastModified(),
      )
    }

  suspend fun inspectKeystoreAliases(
    keystorePath: String,
    storePassword: String,
  ): List<String> =
    withContext(Dispatchers.IO) {
      val ks = loadKeyStore(File(keystorePath), storePassword)
      ks.aliases().toList()
    }

  suspend fun verifyApk(apkFile: File): ApkVerificationSummary =
    withContext(Dispatchers.IO) {
      runCatching {
        val result = ApkVerifier.Builder(apkFile).build().verify()
        val firstCert = result.signerCertificates.firstOrNull()
        val sha256 = firstCert?.let { sha256Hex(it.encoded) }
        val errors =
          (result.errors +
            result.v1SchemeSigners.flatMap { it.errors } +
            result.v2SchemeSigners.flatMap { it.errors } +
            result.v3SchemeSigners.flatMap { it.errors })
            .map { it.toString() }

        ApkVerificationSummary(
          apkName = apkFile.name,
          apkPath = apkFile.absolutePath,
          sizeBytes = apkFile.length(),
          isVerified = result.isVerified,
          verifiedV1 = result.isVerifiedUsingV1Scheme,
          verifiedV2 = result.isVerifiedUsingV2Scheme,
          verifiedV3 = result.isVerifiedUsingV3Scheme,
          signerSha256 = sha256,
          signerSubject = firstCert?.subjectX500Principal?.name,
          errorMessage = if (!result.isVerified) errors.firstOrNull() ?: "Unsigned or invalid signature" else null,
        )
      }.getOrElse { err ->
        ApkVerificationSummary(
          apkName = apkFile.name,
          apkPath = apkFile.absolutePath,
          sizeBytes = apkFile.length(),
          isVerified = false,
          verifiedV1 = false,
          verifiedV2 = false,
          verifiedV3 = false,
          signerSha256 = null,
          signerSubject = null,
          errorMessage = err.message ?: "Failed to read APK",
        )
      }
    }

  suspend fun signApk(
    inputApk: File,
    keystoreFile: File,
    storePassword: String,
    alias: String?,
    keyPassword: String,
  ): SigningResult =
    withContext(Dispatchers.IO) {
      val startMs = android.os.SystemClock.elapsedRealtime()
      require(inputApk.exists() && inputApk.isFile) { "Input APK file does not exist." }
      require(keystoreFile.exists() && keystoreFile.isFile) { "Keystore file does not exist." }

      val ks = loadKeyStore(keystoreFile, storePassword)
      val aliases = ks.aliases().toList()
      val chosenAlias =
        alias?.takeIf { it.isNotBlank() && ks.containsAlias(it) }
          ?: aliases.firstOrNull { ks.isKeyEntry(it) }
          ?: throw IllegalStateException("No private key entry found in keystore.")

      val effectiveKeyPass = keyPassword.ifEmpty { storePassword }
      val privateKey =
        ks.getKey(chosenAlias, effectiveKeyPass.toCharArray()) as? PrivateKey
          ?: throw IllegalStateException("Could not unlock private key for alias '$chosenAlias'.")
      val certChain =
        ks.getCertificateChain(chosenAlias)?.mapNotNull { it as? X509Certificate }
          ?: listOfNotNull(ks.getCertificate(chosenAlias) as? X509Certificate)
      require(certChain.isNotEmpty()) { "Keystore entry '$chosenAlias' has no X.509 certificate." }

      val baseName = inputApk.nameWithoutExtension.removeSuffix("-unsigned").removeSuffix("-signed")
      val outFile = File(apksDir, "${baseName}-signed.apk")

      val signerConfig =
        ApkSigner.SignerConfig.Builder(chosenAlias, privateKey, certChain).build()

      try {
        ApkSigner.Builder(listOf(signerConfig))
          .setInputApk(inputApk)
          .setOutputApk(outFile)
          .setV1SigningEnabled(true)
          .setV2SigningEnabled(true)
          .setV3SigningEnabled(true)
          .build()
          .sign()
      } catch (_: com.android.apksig.apk.MinSdkVersionException) {
        ApkSigner.Builder(listOf(signerConfig))
          .setInputApk(inputApk)
          .setOutputApk(outFile)
          .setMinSdkVersion(24)
          .setV1SigningEnabled(true)
          .setV2SigningEnabled(true)
          .setV3SigningEnabled(true)
          .build()
          .sign()
      }

      val verification = verifyApk(outFile)
      SigningResult(
        signedApkPath = outFile.absolutePath,
        signedApkName = outFile.name,
        verification = verification,
        durationMs = android.os.SystemClock.elapsedRealtime() - startMs,
      )
    }

  suspend fun exportFileToUri(sourceFile: File, destinationUri: Uri): Long =
    withContext(Dispatchers.IO) {
      var copied = 0L
      context.contentResolver.openOutputStream(destinationUri)?.use { out ->
        FileInputStream(sourceFile).use { input -> copied = input.copyTo(out) }
      } ?: throw IllegalStateException("Unable to open destination file.")
      copied
    }

  fun getInstalledAppCertSha256(): String? {
    return runCatching {
      val pm = context.packageManager
      val rawBytes: ByteArray? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          val pkgInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              pm.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
              )
            } else {
              @Suppress("DEPRECATION")
              pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
          val signingInfo = pkgInfo.signingInfo ?: return@runCatching null
          val signers =
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
          signers?.firstOrNull()?.toByteArray()
        } else {
          @Suppress("DEPRECATION")
          val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
          @Suppress("DEPRECATION")
          pkgInfo.signatures?.firstOrNull()?.toByteArray()
        }
      rawBytes?.let { sha256Hex(it) }
    }.getOrNull()
  }

  fun isSignatureCompatibleWithInstalled(summary: ApkVerificationSummary): Boolean? {
    val installedSha = getInstalledAppCertSha256() ?: return null
    val apkSha = summary.signerSha256 ?: return false
    return installedSha.equals(apkSha, ignoreCase = true)
  }

  private fun loadKeyStore(file: File, password: String): KeyStore {
    val types = listOf("PKCS12", "BKS")
    var lastError: Throwable? = null
    for (type in types) {
      val result =
        runCatching {
          val ks = KeyStore.getInstance(type)
          FileInputStream(file).use { fis -> ks.load(fis, password.toCharArray()) }
          ks
        }
      if (result.isSuccess) return result.getOrThrow()
      lastError = result.exceptionOrNull()
    }
    throw IllegalStateException(
      "Unable to open keystore (PKCS12/BKS): ${lastError?.message ?: "invalid password or format"}"
    )
  }

  private fun queryDisplayName(uri: Uri): String? {
    return runCatching {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
      }
    }.getOrNull()
  }

  private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(":") { "%02X".format(it) }
  }

  // Minimal deterministic ASN.1 DER builder for a self-signed X.509 v3 certificate
  private fun buildSelfSignedX509Certificate(
    publicKeyEncoded: ByteArray,
    privateKey: PrivateKey,
    commonName: String,
    organization: String,
    validityYears: Int,
  ): X509Certificate {
    val now = System.currentTimeMillis() - 60_000L
    val notAfter = now + validityYears * 365L * 24L * 60L * 60L * 1000L

    val serialBytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
    serialBytes[0] = (serialBytes[0].toInt() and 0x7F).toByte()
    if (serialBytes.all { it.toInt() == 0 }) serialBytes[7] = 1

    // OID 1.2.840.113549.1.1.11 = sha256WithRSAEncryption
    val sha256RsaAlgId =
      derSequence(
        derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)),
        byteArrayOf(0x05, 0x00), // NULL
      )

    val name =
      derSequence(
        derRdn(oid = byteArrayOf(0x55, 0x04, 0x03), value = commonName), // CN
        derRdn(oid = byteArrayOf(0x55, 0x04, 0x0A), value = organization), // O
      )

    val validity =
      derSequence(
        derUtcTime(Date(now)),
        derUtcTime(Date(notAfter)),
      )

    // Version v3 = [0] EXPLICIT INTEGER 2
    val version = derTagged(0xA0, derInteger(BigInteger.valueOf(2)))

    val tbsCertificate =
      derSequence(
        version,
        derInteger(BigInteger(1, serialBytes)),
        sha256RsaAlgId,
        name,
        validity,
        name,
        publicKeyEncoded,
      )

    val sig =
      Signature.getInstance("SHA256withRSA").apply {
        initSign(privateKey)
        update(tbsCertificate)
      }.sign()

    val certDer =
      derSequence(
        tbsCertificate,
        sha256RsaAlgId,
        derBitString(sig),
      )

    val factory = CertificateFactory.getInstance("X.509")
    return factory.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
  }

  private fun derRdn(oid: ByteArray, value: String): ByteArray {
    val attr = derSequence(derOid(oid), derUtf8String(value))
    return derWrap(0x31, attr) // SET
  }

  private fun derOid(encodedOidBytes: ByteArray): ByteArray = derWrap(0x06, encodedOidBytes)

  private fun derUtf8String(value: String): ByteArray =
    derWrap(0x0C, value.toByteArray(StandardCharsets.UTF_8))

  private fun derUtcTime(date: Date): ByteArray {
    val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    return derWrap(0x17, fmt.format(date).toByteArray(StandardCharsets.US_ASCII))
  }

  private fun derInteger(value: BigInteger): ByteArray = derWrap(0x02, value.toByteArray())

  private fun derBitString(bytes: ByteArray): ByteArray {
    val out = ByteArray(bytes.size + 1)
    out[0] = 0x00 // 0 unused bits
    System.arraycopy(bytes, 0, out, 1, bytes.size)
    return derWrap(0x03, out)
  }

  private fun derTagged(tag: Int, content: ByteArray): ByteArray = derWrap(tag, content)

  private fun derSequence(vararg parts: ByteArray): ByteArray {
    val body = ByteArrayOutputStream()
    parts.forEach { body.write(it) }
    return derWrap(0x30, body.toByteArray())
  }

  private fun derWrap(tag: Int, content: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(tag)
    val len = content.size
    when {
      len < 0x80 -> out.write(len)
      len <= 0xFF -> {
        out.write(0x81)
        out.write(len)
      }
      len <= 0xFFFF -> {
        out.write(0x82)
        out.write((len ushr 8) and 0xFF)
        out.write(len and 0xFF)
      }
      else -> {
        out.write(0x83)
        out.write((len ushr 16) and 0xFF)
        out.write((len ushr 8) and 0xFF)
        out.write(len and 0xFF)
      }
    }
    out.write(content)
    return out.toByteArray()
  }
}
