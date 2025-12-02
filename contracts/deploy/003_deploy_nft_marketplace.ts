import { HardhatRuntimeEnvironment } from "hardhat/types";
import { DeployFunction } from "hardhat-deploy/types";

const func: DeployFunction = async function (hre: HardhatRuntimeEnvironment) {
  const { deployments, getNamedAccounts } = hre;
  const { deploy } = deployments;
  const { deployer, treasury } = await getNamedAccounts();

  console.log("Deploying WaqitiNFTMarketplace with account:", deployer);

  const marketplace = await deploy("WaqitiNFTMarketplace", {
    from: deployer,
    proxy: {
      proxyContract: "OpenZeppelinTransparentProxy",
      execute: {
        init: {
          methodName: "initialize",
          args: [
            deployer, // admin
            250, // platformFee (2.5%)
            treasury || deployer, // feeRecipient
          ],
        },
      },
    },
    log: true,
    autoMine: true,
  });

  console.log("WaqitiNFTMarketplace deployed to:", marketplace.address);
};

export default func;
func.tags = ["WaqitiNFTMarketplace", "NFT"];
func.dependencies = [];