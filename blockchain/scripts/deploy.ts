import { network } from "hardhat";

const { ethers } = await network.create();

async function main() {
  console.log("Deploying CertificateRegistry...");

  const registry = await ethers.deployContract("CertificateRegistry");

  await registry.waitForDeployment();

  const address = await registry.getAddress();

  console.log("CertificateRegistry deployed successfully!");
  console.log("Contract address:", address);
}

main().catch((error) => {
  console.error("Deployment failed:", error);
  process.exitCode = 1;
});
