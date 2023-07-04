	@Subscribe
	public void onProjectileMoved(ProjectileMoved projectileMoved)
	{
		Projectile projectile = projectileMoved.getProjectile();
		log.info("----- NEW PROJECTILE -----");
		log.info("getId: "+ projectile.getId());
		log.info("getFloor: "+ projectile.getFloor());
		log.info("getX1: "+ projectile.getAnimation());
		log.info("getY1: "+ projectile.getY1());
		log.info("getHeight: "+ projectile.getHeight());
		log.info("getStartCycle: "+ projectile.getStartCycle());
		log.info("getEndCycle: "+ projectile.getEndCycle());
		log.info("getSlope: "+ projectile.getSlope());
		log.info("getStartHeight: "+ projectile.getStartHeight());
		log.info("getEndHeight: "+ projectile.getEndHeight());

		int plane = client.getPlane();
		int sceneX = client.getLocalPlayer().getLocalLocation().getSceneX();
		int sceneY = client.getLocalPlayer().getLocalLocation().getSceneY();
		int tileHeight = client.getTileHeights()[plane][sceneX][sceneY];
		log.info("tileHeight: "+ tileHeight);
	}
