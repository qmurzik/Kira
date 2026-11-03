import Foundation
import CZlib
#if canImport(CommonCrypto)
import CommonCrypto
#endif

/// Традиционное шифрование PKWARE (ZipCrypto). Только расшифровка.
struct TraditionalZipDecryptor {
    private var k0: UInt32 = 0x12345678
    private var k1: UInt32 = 0x23456789
    private var k2: UInt32 = 0x34567890

    private static let crcTable: [UInt32] = {
        (0..<256).map { n -> UInt32 in
            var c = UInt32(n)
            for _ in 0..<8 { c = (c & 1) != 0 ? 0xEDB88320 ^ (c >> 1) : c >> 1 }
            return c
        }
    }()

    init(password: Data) {
        for b in password { update(b) }
    }

    private mutating func update(_ b: UInt8) {
        k0 = Self.crcTable[Int((k0 ^ UInt32(b)) & 0xFF)] ^ (k0 >> 8)
        k1 = (k1 &+ (k0 & 0xFF)) &* 134775813 &+ 1
        k2 = Self.crcTable[Int((k2 ^ (k1 >> 24)) & 0xFF)] ^ (k2 >> 8)
    }

    private func streamByte() -> UInt8 {
        let t = UInt16(truncatingIfNeeded: k2 | 2)
        return UInt8(truncatingIfNeeded: (UInt32(t) &* UInt32(t ^ 1)) >> 8)
    }

    mutating func decrypt(_ data: inout Data) {
        data.withUnsafeMutableBytes { raw in
            guard let p = raw.bindMemory(to: UInt8.self).baseAddress else { return }
            for i in 0..<raw.count {
                let c = p[i] ^ streamByte()
                update(c)
                p[i] = c
            }
        }
    }
}

/// Расшифровка WinZip AES (AE-1/AE-2). Требует CommonCrypto (iOS/macOS).
final class WinZipAESDecryptor {
    #if canImport(CommonCrypto)
    private var cryptor: CCCryptorRef?
    private var counter = [UInt8](repeating: 0, count: 16)
    private var keystream = [UInt8](repeating: 0, count: 16)
    private var keystreamPos = 16
    private var hmac = CCHmacContext()
    let expectedVerifier: Data

    static func saltLength(strength: Int) -> Int { [1: 8, 2: 12, 3: 16][strength] ?? 16 }
    static func keyLength(strength: Int) -> Int { [1: 16, 2: 24, 3: 32][strength] ?? 32 }

    /// Возвращает nil, если пароль неверный (не совпал проверочный код).
    init?(password: Data, salt: Data, verifier: Data, strength: Int) {
        let keyLen = Self.keyLength(strength: strength)
        var derived = [UInt8](repeating: 0, count: keyLen * 2 + 2)
        let rc = password.withUnsafeBytes { pw in
            salt.withUnsafeBytes { s in
                CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2),
                                     pw.baseAddress?.assumingMemoryBound(to: CChar.self), password.count,
                                     s.baseAddress?.assumingMemoryBound(to: UInt8.self), salt.count,
                                     CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA1), 1000,
                                     &derived, derived.count)
            }
        }
        guard rc == kCCSuccess else { return nil }
        expectedVerifier = Data(derived[(keyLen * 2)..<(keyLen * 2 + 2)])
        guard expectedVerifier == verifier else { return nil }
        let encKey = Array(derived[0..<keyLen])
        let macKey = Array(derived[keyLen..<(keyLen * 2)])
        var ref: CCCryptorRef?
        let st = CCCryptorCreate(CCOperation(kCCEncrypt), CCAlgorithm(kCCAlgorithmAES), CCOptions(kCCOptionECBMode),
                                 encKey, encKey.count, nil, &ref)
        guard st == kCCSuccess else { return nil }
        cryptor = ref
        CCHmacInit(&hmac, CCHmacAlgorithm(kCCHmacAlgSHA1), macKey, macKey.count)
    }

    deinit { if let cryptor { CCCryptorRelease(cryptor) } }

    private func nextKeystreamBlock() {
        // Счётчик WinZip: 128-битное little-endian число, начиная с 1.
        for i in 0..<16 {
            counter[i] = counter[i] &+ 1
            if counter[i] != 0 { break }
        }
        var moved = 0
        CCCryptorUpdate(cryptor, counter, 16, &keystream, 16, &moved)
        keystreamPos = 0
    }

    func decrypt(_ data: inout Data) {
        data.withUnsafeBytes { raw in
            CCHmacUpdate(&hmac, raw.baseAddress, raw.count)
        }
        data.withUnsafeMutableBytes { raw in
            guard let p = raw.bindMemory(to: UInt8.self).baseAddress else { return }
            for i in 0..<raw.count {
                if keystreamPos == 16 { nextKeystreamBlock() }
                p[i] ^= keystream[keystreamPos]
                keystreamPos += 1
            }
        }
    }

    func verify(authCode: Data) -> Bool {
        var mac = [UInt8](repeating: 0, count: Int(CC_SHA1_DIGEST_LENGTH))
        CCHmacFinal(&hmac, &mac)
        return Data(mac.prefix(10)) == authCode
    }

    static var isSupported: Bool { true }
    #else
    let expectedVerifier = Data()
    static func saltLength(strength: Int) -> Int { [1: 8, 2: 12, 3: 16][strength] ?? 16 }
    init?(password: Data, salt: Data, verifier: Data, strength: Int) { return nil }
    func decrypt(_ data: inout Data) {}
    func verify(authCode: Data) -> Bool { false }
    static var isSupported: Bool { false }
    #endif
}

/// CRC-32 через zlib.
struct CRC32 {
    private(set) var value: UInt32 = 0

    mutating func update(_ data: Data) {
        data.withUnsafeBytes { raw in
            guard let base = raw.bindMemory(to: UInt8.self).baseAddress, raw.count > 0 else { return }
            value = UInt32(truncatingIfNeeded: crc32(uLong(value), base, uInt(raw.count)))
        }
    }

    static func checksum(_ data: Data) -> UInt32 {
        var c = CRC32()
        c.update(data)
        return c.value
    }
}
