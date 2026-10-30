import { network } from "hardhat";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function main() {
  const { ethers } = await network.create();
  const [deployer] = await ethers.getSigners();

  if (!deployer) {
    throw new Error(
      "No deployer account found. Ensure SEPOLIA_RPC_URL and PRIVATE_KEY environment variables are configured.",
    );
  }

  const networkName = network.name || "unknown";
  console.log(`====================================================`);
  console.log(`CertChain — Smart Contract Testnet Deployment`);
  console.log(`====================================================`);
  console.log(`Deploying network: ${networkName}`);
  console.log(`Deployer address: ${deployer.address}`);

  const balance = await ethers.provider.getBalance(deployer.address);
  console.log(`Deployer balance: ${ethers.formatEther(balance)} ETH`);

  if (balance === 0n) {
    console.warn(
      `WARNING: Deployer account balance is 0. Ensure account has testnet faucet ETH before deploying.`,
    );
  }

  console.log(`
Deploying CertificateRegistry contract...`);
  const factory = await ethers.getContractFactory("CertificateRegistry");
  const contract = await factory.deploy();

  console.log(`Transaction submitted. Waiting for deployment confirmation...`);
  await contract.waitForDeployment();

  const contractAddress = await contract.getAddress();
  const deploymentTx = contract.deploymentTransaction();

  console.log(`
✓ CertificateRegistry successfully deployed!`);
  console.log(`Contract Address: ${contractAddress}`);
  console.log(`Transaction Hash: ${deploymentTx?.hash || "N/A"}`);
  console.log(`Owner:            ${deployer.address}`);

  // Save deployment artifact
  const deploymentRecord = {
    network: networkName,
    contractAddress,
    transactionHash: deploymentTx?.hash || "",
    deployerAddress: deployer.address,
    timestamp: new Date().toISOString(),
    contractName: "CertificateRegistry",
    solcVersion: "0.8.34",
  };

  const deploymentsDir = path.join(__dirname, "..", "deployments");
  if (!fs.existsSync(deploymentsDir)) {
    fs.mkdirSync(deploymentsDir, { recursive: true });
  }

  const outputPath = path.join(deploymentsDir, `${networkName}-deployment.json`);
  fs.writeFileSync(outputPath, JSON.stringify(deploymentRecord, null, 2));
  console.log(`
Deployment artifact written to: ${outputPath}`);
  console.log(`
To update your backend application.properties:`);
  console.log(`BLOCKCHAIN_CONTRACT_ADDRESS=${contractAddress}`);
  console.log(`====================================================`);
}

main().catch((error) => {
  console.error("Testnet deployment failed:", error);
  process.exitCode = 1;
});
