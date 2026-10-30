// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/**
 * @title CertificateRegistry
 * @dev Cryptographic certificate registry with role-based issuer authorization.
 * Only the contract owner or explicitly authorized issuers can register certificates.
 * Revocation is restricted to the contract owner, issuing authority, or authorized issuers.
 */
contract CertificateRegistry {
    struct Certificate {
        bytes32 certificateHash;
        address issuer;
        uint256 issuedAt;
        bool revoked;
    }

    mapping(string => Certificate) private certificates;
    mapping(address => bool) public authorizedIssuers;

    address public owner;

    event CertificateIssued(
        string certificateId,
        bytes32 certificateHash,
        address indexed issuer,
        uint256 issuedAt
    );

    event CertificateRevoked(
        string certificateId,
        address indexed revokedBy
    );

    event IssuerAuthorized(address indexed issuer, address indexed authorizedBy);
    event IssuerDeauthorized(address indexed issuer, address indexed deauthorizedBy);

    modifier onlyOwner() {
        require(msg.sender == owner, "Only owner authorized");
        _;
    }

    modifier onlyAuthorizedIssuer() {
        require(
            msg.sender == owner || authorizedIssuers[msg.sender],
            "Not authorized issuer"
        );
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    /**
     * @dev Authorize a new certificate issuing address. Only callable by contract owner.
     */
    function authorizeIssuer(address issuer) external onlyOwner {
        require(issuer != address(0), "Invalid issuer address");
        require(!authorizedIssuers[issuer], "Issuer already authorized");
        authorizedIssuers[issuer] = true;
        emit IssuerAuthorized(issuer, msg.sender);
    }

    /**
     * @dev Revoke issuance authority for an address. Only callable by contract owner.
     */
    function deauthorizeIssuer(address issuer) external onlyOwner {
        require(authorizedIssuers[issuer], "Issuer not authorized");
        authorizedIssuers[issuer] = false;
        emit IssuerDeauthorized(issuer, msg.sender);
    }

    /**
     * @dev Query whether an address is an authorized certificate issuer.
     */
    function isAuthorizedIssuer(address issuer) external view returns (bool) {
        return issuer == owner || authorizedIssuers[issuer];
    }

    /**
     * @dev Issue a new certificate with canonical hash. Restricted to authorized issuers.
     */
    function issueCertificate(
        string calldata certificateId,
        bytes32 certificateHash
    ) external onlyAuthorizedIssuer {
        require(bytes(certificateId).length > 0, "Certificate ID required");
        require(certificateHash != bytes32(0), "Certificate hash required");
        require(
            certificates[certificateId].issuedAt == 0,
            "Certificate already exists"
        );

        certificates[certificateId] = Certificate({
            certificateHash: certificateHash,
            issuer: msg.sender,
            issuedAt: block.timestamp,
            revoked: false
        });

        emit CertificateIssued(
            certificateId,
            certificateHash,
            msg.sender,
            block.timestamp
        );
    }

    /**
     * @dev Verify if a certificate exists, matches hash, and is not revoked. Public view.
     */
    function verifyCertificate(
        string calldata certificateId,
        bytes32 certificateHash
    ) external view returns (bool) {
        Certificate storage certificate = certificates[certificateId];

        return
            certificate.issuedAt != 0 &&
            certificate.certificateHash == certificateHash &&
            !certificate.revoked;
    }

    /**
     * @dev Revoke a certificate. Only callable by owner or the original certificate issuer.
     */
    function revokeCertificate(string calldata certificateId) external {
        Certificate storage certificate = certificates[certificateId];

        require(certificate.issuedAt != 0, "Certificate does not exist");
        require(!certificate.revoked, "Certificate already revoked");
        require(
            msg.sender == owner || msg.sender == certificate.issuer,
            "Not authorized"
        );

        certificate.revoked = true;

        emit CertificateRevoked(certificateId, msg.sender);
    }

    /**
     * @dev Fetch stored certificate details. Public view.
     */
    function getCertificate(
        string calldata certificateId
    )
        external
        view
        returns (
            bytes32 certificateHash,
            address issuer,
            uint256 issuedAt,
            bool revoked
        )
    {
        Certificate storage certificate = certificates[certificateId];

        return (
            certificate.certificateHash,
            certificate.issuer,
            certificate.issuedAt,
            certificate.revoked
        );
    }
}
