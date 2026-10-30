import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { network } from "hardhat";

describe("CertificateRegistry", async function () {
  const { ethers } = await network.create();

  async function deployContract() {
    const registry = await ethers.deployContract("CertificateRegistry");
    await registry.waitForDeployment();
    return registry;
  }

  it("should deploy and set the owner", async function () {
    const registry = await deployContract();
    const [owner] = await ethers.getSigners();

    assert.equal(await registry.owner(), owner.address);
  });

  it("should issue and verify a certificate", async function () {
    const registry = await deployContract();
    const certificateId = "CERT-2026-10001";
    const certificateHash = ethers.keccak256(
      ethers.toUtf8Bytes("sample-certificate"),
    );

    await registry.issueCertificate(certificateId, certificateHash);

    const isValid = await registry.verifyCertificate(
      certificateId,
      certificateHash,
    );

    assert.equal(isValid, true);
  });

  it("should reject verification with an incorrect hash", async function () {
    const registry = await deployContract();
    const certificateId = "CERT-2026-10002";

    const correctHash = ethers.keccak256(
      ethers.toUtf8Bytes("correct-certificate"),
    );
    const incorrectHash = ethers.keccak256(
      ethers.toUtf8Bytes("incorrect-certificate"),
    );

    await registry.issueCertificate(certificateId, correctHash);

    const isValid = await registry.verifyCertificate(
      certificateId,
      incorrectHash,
    );

    assert.equal(isValid, false);
  });

  it("should reject a duplicate certificate ID", async function () {
    const registry = await deployContract();
    const certificateId = "CERT-2026-10003";
    const certificateHash = ethers.keccak256(
      ethers.toUtf8Bytes("sample-certificate"),
    );

    await registry.issueCertificate(certificateId, certificateHash);

    await assert.rejects(
      registry.issueCertificate(certificateId, certificateHash),
    );
  });

  it("should revoke a certificate", async function () {
    const registry = await deployContract();
    const certificateId = "CERT-2026-10004";
    const certificateHash = ethers.keccak256(
      ethers.toUtf8Bytes("sample-certificate"),
    );

    await registry.issueCertificate(certificateId, certificateHash);
    await registry.revokeCertificate(certificateId);

    const isValid = await registry.verifyCertificate(
      certificateId,
      certificateHash,
    );

    assert.equal(isValid, false);
  });

  it("should allow owner to authorize and deauthorize an issuer", async function () {
    const registry = await deployContract();
    const [, issuer] = await ethers.getSigners();

    assert.equal(await registry.isAuthorizedIssuer(issuer.address), false);

    await registry.authorizeIssuer(issuer.address);
    assert.equal(await registry.isAuthorizedIssuer(issuer.address), true);

    await registry.deauthorizeIssuer(issuer.address);
    assert.equal(await registry.isAuthorizedIssuer(issuer.address), false);
  });

  it("should allow an authorized issuer to issue certificates", async function () {
    const registry = await deployContract();
    const [, issuer] = await ethers.getSigners();
    const certId = "CERT-2026-AUTH-01";
    const certHash = ethers.keccak256(ethers.toUtf8Bytes("authorized-cert"));

    await registry.authorizeIssuer(issuer.address);

    const issuerRegistry = registry.connect(issuer);
    await issuerRegistry.issueCertificate(certId, certHash);

    const isValid = await registry.verifyCertificate(certId, certHash);
    assert.equal(isValid, true);

    const certData = await registry.getCertificate(certId);
    assert.equal(certData[1], issuer.address);
  });

  it("should reject issuance from an unauthorized caller", async function () {
    const registry = await deployContract();
    const [, , unauthorized] = await ethers.getSigners();
    const certId = "CERT-2026-UNAUTH-01";
    const certHash = ethers.keccak256(ethers.toUtf8Bytes("unauthorized-cert"));

    const unauthorizedRegistry = registry.connect(unauthorized);
    await assert.rejects(
      unauthorizedRegistry.issueCertificate(certId, certHash),
      /Not authorized issuer/
    );
  });

  it("should reject revocation from an unauthorized caller", async function () {
    const registry = await deployContract();
    const [, issuer, attacker] = await ethers.getSigners();
    const certId = "CERT-2026-REVOKE-FAIL";
    const certHash = ethers.keccak256(ethers.toUtf8Bytes("secure-cert"));

    await registry.authorizeIssuer(issuer.address);
    const issuerRegistry = registry.connect(issuer);
    await issuerRegistry.issueCertificate(certId, certHash);

    const attackerRegistry = registry.connect(attacker);
    await assert.rejects(
      attackerRegistry.revokeCertificate(certId),
      /Not authorized/
    );
  });

  it("should reject issuer authorization attempts by non-owners", async function () {
    const registry = await deployContract();
    const [, nonOwner, candidate] = await ethers.getSigners();

    const nonOwnerRegistry = registry.connect(nonOwner);
    await assert.rejects(
      nonOwnerRegistry.authorizeIssuer(candidate.address),
      /Only owner authorized/
    );
  });
});
